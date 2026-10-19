package com.wifisense.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wifisense.analysis.*;
import com.wifisense.dto.AgentReportAck;
import com.wifisense.dto.AgentReportRequest;
import com.wifisense.model.AgentDevice;
import com.wifisense.model.AgentKey;
import com.wifisense.model.AgentReport;
import com.wifisense.model.AlertType;
import com.wifisense.model.AnomalyType;
import com.wifisense.repository.AgentDeviceRepository;
import com.wifisense.repository.AgentKeyRepository;
import com.wifisense.repository.AgentReportRepository;
import com.wifisense.repository.HomeNetworkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * Receives the reports of the agent and puts the AI to work on them: a security audit (encryption, router ports,
 * new devices), the quality of the connection, the best Wi-Fi channel and anomaly detection over the recent history
 * of that network.
 * The AI calls run outside any database transaction, and if the AI is down the report is saved without verdicts.
 */
@Service
public class AgentReportService {

    static final Set<String> SECURITY_GRADES = AgentInsights.SECURITY_GRADES;
    private static final Logger log = LoggerFactory.getLogger(AgentReportService.class);

    private final AgentKeyService keyService;
    private final AgentReportThrottle throttle;
    private final AgentKeyRepository keys;
    private final AgentReportRepository reports;
    private final AgentDeviceRepository devices;
    private final HomeNetworkRepository networks;
    private final AiAnalysisClient ai;
    private final AgentInsights insights;
    private final AgentAlertService alerts;
    private final TransactionTemplate transaction;
    private final AgentProperties properties;
    private final DeviceSessionTracker sessions;
    private final ObjectMapper json;
    private final Clock clock;

    public AgentReportService(AgentKeyService keyService, AgentReportThrottle throttle, AgentKeyRepository keys,
                              AgentReportRepository reports, AgentDeviceRepository devices,
                              HomeNetworkRepository networks, AiAnalysisClient ai, AgentAlertService alerts,
                              TransactionTemplate transaction, AgentProperties properties, ObjectMapper json,
                              Clock clock, DeviceSessionTracker sessions) {
        this.keyService = keyService;
        this.throttle = throttle;
        this.keys = keys;
        this.reports = reports;
        this.devices = devices;
        this.networks = networks;
        this.ai = ai;
        this.insights = new AgentInsights(ai, json);
        this.alerts = alerts;
        this.transaction = transaction;
        this.properties = properties;
        this.sessions = sessions;
        this.json = json;
        this.clock = clock;
    }

    public AgentReportAck receive(String rawKey, AgentReportRequest request) {
        AgentKey key = keyService.authenticate(rawKey);
        throttle.acquire(key.getId());
        Long networkId = key.getNetwork().getId();
        Instant now = Instant.now(clock);

        List<String> hashes = request.deviceHashesOrEmpty().stream().distinct().toList();
        Map<String, String> vendors = request.vendorsByFingerprint();
        Map<String, String> names = request.namesByFingerprint();
        Set<String> privates = request.privateFingerprints();
        int deviceCount = Math.min(Math.max(request.deviceCount(), hashes.size()), 1024);
        Prepared prepared = Objects.requireNonNull(transaction.execute(status -> {
            DeviceOutcome outcome = registerDevices(networkId, hashes, vendors, names, privates, now);
            return new Prepared(outcome.newDevices(), outcome.unauthorizedPresent(),
                    reports.findByNetworkIdOrderByReceivedAtDesc(networkId,
                            PageRequest.of(0, Math.max(properties.analysisWindow() - 1, 1))));
        }));
        int newDevices = Math.min(prepared.newDevices(), deviceCount);

        ObjectNode details = json.createObjectNode();
        details.set("wifi", insights.wifiDetails(request));
        details.set("openPorts", json.valueToTree(request.openPortsOrEmpty().stream().distinct().sorted().toList()));
        JsonNode security = insights.assessSecurity(request, deviceCount, newDevices);
        JsonNode channel = insights.recommendChannel(request);
        JsonNode quality = insights.scoreQuality(request);
        AiAnalysisResponse anomaly = detectAnomaly(networkId, request, deviceCount, now, prepared.previous());
        details.set("security", security);
        details.set("quality", quality);
        details.set("anomaly", anomaly == null ? null : anomalyDetails(anomaly));
        details.set("channel", channel);

        AgentReport saved = Objects.requireNonNull(transaction.execute(status -> {
            AgentReport report = new AgentReport(networks.getReferenceById(networkId), now, request.agentVersion());
            report.wifi(request.ssid(), request.authentication(), request.band(), request.channel(),
                    request.signalDbm());
            report.connection(request.latencyMs(), request.jitterMs(), request.packetLossPct(),
                    request.gatewayLatencyMs());
            report.devices(deviceCount, newDevices);
            if (security != null) {
                report.security(security.get("score").asInt(), security.get("grade").asText());
            }
            if (quality != null) {
                report.quality(quality.get("score").asInt());
            }
            if (anomaly != null) {
                report.anomaly(anomaly.anomalyDetected(),
                        AnomalyType.fromAi(anomaly.anomalyType(), anomaly.anomalyDetected()).name());
            }
            report.details(details.toString());
            keys.markSeen(key.getId(), now);
            return reports.save(report);
        }));
        raiseAlerts(networkId, newDevices, prepared.unauthorizedPresent(), security, saved.getAnomalyDetected(),
                anomaly);
        return new AgentReportAck(saved.getId(), deviceCount, newDevices, saved.getSecurityScore(),
                saved.getSecurityGrade(), saved.getAnomalyDetected(), summary(deviceCount, newDevices, security));
    }

    /** Turns this report's findings into alerts (recorded and, when due, emailed). Never breaks the report. */
    private void raiseAlerts(Long networkId, int newDevices, int unauthorizedPresent, JsonNode security,
                             Boolean anomalyDetected, AiAnalysisResponse anomaly) {
        List<AgentAlertService.Signal> signals = new ArrayList<>();
        if (newDevices > 0) {
            signals.add(new AgentAlertService.Signal(AlertType.NEW_DEVICE, newDevices == 1
                    ? "Apareció 1 equipo nuevo en tu red." : "Aparecieron " + newDevices + " equipos nuevos en tu red."));
        }
        if (unauthorizedPresent > 0) {
            signals.add(new AgentAlertService.Signal(AlertType.UNAUTHORIZED_DEVICE, unauthorizedPresent == 1
                    ? "El equipo que marcaste como no autorizado sigue conectado."
                    : unauthorizedPresent + " equipos que marcaste como no autorizados siguen conectados."));
        }
        if (security != null && "CRITICAL".equals(security.path("grade").asText())) {
            signals.add(new AgentAlertService.Signal(AlertType.SECURITY,
                    security.path("summary").asText("La seguridad de tu red está en riesgo.")));
        }
        if (Boolean.TRUE.equals(anomalyDetected) && anomaly != null) {
            signals.add(new AgentAlertService.Signal(AlertType.ANOMALY,
                    anomaly.recommendation() != null ? anomaly.recommendation() : anomaly.message()));
        }
        try {
            alerts.process(networkId, signals);
        } catch (RuntimeException e) {
            log.warn("Could not raise alerts for network {}: {}", networkId, e.getMessage());
        }
    }

    private record Prepared(int newDevices, int unauthorizedPresent, List<AgentReport> previous) {
    }

    private record DeviceOutcome(int newDevices, int unauthorizedPresent) {
    }

    /**
     * Records every device of the report and returns how many had never been seen on this network. The first report
     * of a network only learns its devices: they are all "new", but announcing them would be noise.
     */
    private DeviceOutcome registerDevices(Long networkId, List<String> hashes, Map<String, String> vendors,
                                          Map<String, String> names, Set<String> privates, Instant now) {
        boolean learning = !devices.existsByNetworkId(networkId);
        Map<String, AgentDevice> known = new HashMap<>();
        devices.findByNetworkIdOrderByFirstSeenAtAsc(networkId).forEach(d -> known.put(d.getFingerprint(), d));
        int fresh = 0;
        int unauthorized = 0;
        for (String hash : hashes) {
            AgentDevice device = known.get(hash);
            if (device == null) {
                device = new AgentDevice(networkId, hash, now);
                device.identify(vendors.get(hash), names.get(hash));
                device.markPrivateMac(privates.contains(hash));
                devices.save(device);
                fresh++;
            } else {
                device.seen(now);
                device.identify(vendors.get(hash), names.get(hash));  // fills maker/name for older devices
                device.markPrivateMac(privates.contains(hash));
                if (!device.isAuthorized()) {
                    unauthorized++;
                }
            }
        }
        devices.flush();  // the periods below reference the devices just saved
        sessions.track(DeviceSessionTracker.Owner.HOME, networkId, hashes, now);
        return new DeviceOutcome(learning ? 0 : fresh, unauthorized);
    }

    /** Isolation Forest on this report, with the previous complete reports of the network as context. */
    private AiAnalysisResponse detectAnomaly(Long networkId, AgentReportRequest request, int deviceCount, Instant now,
                                             List<AgentReport> previous) {
        if (request.latencyMs() == null || request.jitterMs() == null || request.packetLossPct() == null) {
            return null;
        }
        List<AiAnalysisRequest.Sample> window = new ArrayList<>();
        for (int i = previous.size() - 1; i >= 0; i--) {
            AgentReport old = previous.get(i);
            if (old.getLatencyMs() != null && old.getJitterMs() != null && old.getPacketLossPct() != null) {
                window.add(sample(old.getLatencyMs(), old.getJitterMs(), old.getPacketLossPct(), old.getSignalDbm(),
                        old.getDeviceCount(), old.getReceivedAt()));
            }
        }
        window.add(sample(request.latencyMs(), request.jitterMs(), request.packetLossPct(), request.signalDbm(),
                deviceCount, now));
        try {
            return ai.detectAnomalies(new AiAnalysisRequest(networkId, false, window));
        } catch (AiServiceUnavailableException e) {
            log.warn("Agent report saved without anomaly detection: {}", e.getMessage());
            return null;
        }
    }

    private static AiAnalysisRequest.Sample sample(double latency, double jitter, double loss, Integer signal,
                                                   int devices, Instant at) {
        return new AiAnalysisRequest.Sample(latency, jitter, loss, null, signal, null, devices, null, at);
    }

    private ObjectNode anomalyDetails(AiAnalysisResponse anomaly) {
        ObjectNode node = json.createObjectNode();
        node.put("detected", anomaly.anomalyDetected());
        node.put("severity", anomaly.severity() == null ? null : anomaly.severity().name());
        node.put("type", AnomalyType.fromAi(anomaly.anomalyType(), anomaly.anomalyDetected()).name());
        node.put("diagnosis", anomaly.diagnosis());
        node.put("message", anomaly.message());
        node.put("recommendation", anomaly.recommendation());
        node.put("recommendationSource", anomaly.recommendationSource());
        node.put("modelVersion", anomaly.modelVersion());
        return node;
    }

    static String summary(int deviceCount, int newDevices, JsonNode security) {
        StringBuilder text = new StringBuilder(deviceCount == 1 ? "1 equipo conectado" : deviceCount + " equipos conectados");
        if (newDevices > 0) {
            text.append(newDevices == 1 ? " (1 nuevo)" : " (" + newDevices + " nuevos)");
        }
        text.append('.');
        if (security != null) {
            text.append(" Seguridad: ").append(security.path("gradeLabel").asText(security.path("grade").asText()))
                    .append(" (").append(security.path("score").asInt()).append("/100).");
        }
        return text.toString();
    }
}
