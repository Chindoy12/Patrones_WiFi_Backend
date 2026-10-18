package com.wifisense.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wifisense.analysis.AiAnalysisClient;
import com.wifisense.analysis.AnalysisType;
import com.wifisense.analysis.NetworkAnalysisFacade;
import com.wifisense.dto.AgentReportAck;
import com.wifisense.dto.AgentReportRequest;
import com.wifisense.dto.AnalysisResultResponse;
import com.wifisense.dto.MeasurementResponse;
import com.wifisense.model.Alert;
import com.wifisense.model.NetworkSensor;
import com.wifisense.model.NetworkStatus;
import com.wifisense.model.SensorReport;
import com.wifisense.network.DataSourceType;
import com.wifisense.network.NetworkSnapshot;
import com.wifisense.repository.AlertRepository;
import com.wifisense.repository.NetworkRepository;
import com.wifisense.repository.SensorReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Reports of the sensor of a team network. Each one becomes a real measurement of that network (latency, jitter and
 * loss, signal, link rate and connected devices) plus the traffic of the sensor's computer and its packets per
 * protocol, and then the same threshold analysis the monitoring runs: alerts and the network state react as usual.
 * <p>
 * Like Mi Wi-Fi, the report is also kept with what the AI says about it (security audit, quality and best channel)
 * and the devices the sensor sees, known only by a salted fingerprint of their MAC. A new device, a device marked as
 * not authorized that is connected, or a critical security grade becomes an alert of the team. The AI is called
 * outside any transaction and, when it is down, everything is saved without its verdicts.
 */
@Service
public class SensorReportService {

    private static final Logger log = LoggerFactory.getLogger(SensorReportService.class);
    private static final double BYTES_PER_MB = 1024.0 * 1024.0;

    private final NetworkSensorService sensors;
    private final AgentReportThrottle throttle;
    private final MeasurementCollectionService collection;
    private final NetworkAnalysisFacade analysis;
    private final SensorDeviceService devices;
    private final SensorReportRepository reports;
    private final NetworkRepository networks;
    private final AlertRepository alerts;
    private final AgentInsights insights;
    private final ObjectMapper json;
    private final Clock clock;

    public SensorReportService(NetworkSensorService sensors, AgentReportThrottle throttle,
                               MeasurementCollectionService collection, NetworkAnalysisFacade analysis,
                               SensorDeviceService devices, SensorReportRepository reports, NetworkRepository networks,
                               AlertRepository alerts, AiAnalysisClient ai, ObjectMapper json, Clock clock) {
        this.sensors = sensors;
        this.throttle = throttle;
        this.collection = collection;
        this.analysis = analysis;
        this.devices = devices;
        this.reports = reports;
        this.networks = networks;
        this.alerts = alerts;
        this.insights = new AgentInsights(ai, json);
        this.json = json;
        this.clock = clock;
    }

    /** Empty when the key is not the key of a sensor (then it is an agent of Mi Wi-Fi, or nothing). */
    public Optional<AgentReportAck> receive(String rawKey, AgentReportRequest request) {
        Optional<NetworkSensor> found = sensors.authenticate(rawKey);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        NetworkSensor sensor = found.get();
        throttle.acquireSensor(sensor.getId());
        Long networkId = sensor.getNetwork().getId();
        String ssid = sensor.getNetwork().getSsid();
        Instant now = Instant.now(clock);
        sensors.markSeen(sensor.getId(), now, request.agentVersion(), request.platform());

        SensorDeviceService.Outcome seen = devices.register(networkId, request, now);
        int deviceCount = Math.min(Math.max(request.deviceCount(), request.deviceHashesOrEmpty().size()), 1024);
        int newDevices = Math.min(seen.newDevices(), deviceCount);
        boolean measured = request.latencyMs() != null && request.jitterMs() != null && request.packetLossPct() != null;
        MeasurementResponse measurement = null;
        NetworkStatus status = null;
        if (measured) {
            measurement = collection.record(networkId, snapshot(request, now), DataSourceType.AGENT);
            status = analyze(networkId);
        }

        JsonNode security = insights.assessSecurity(request, deviceCount, newDevices);
        JsonNode quality = measured ? insights.scoreQuality(request) : null;
        JsonNode channel = insights.recommendChannel(request);
        ObjectNode details = json.createObjectNode();
        details.set("wifi", insights.wifiDetails(request));
        details.set("openPorts", json.valueToTree(request.openPortsOrEmpty().stream().distinct().sorted().toList()));
        details.set("security", security);
        details.set("quality", quality);
        details.set("channel", channel);
        SensorReport report = new SensorReport(networks.getReferenceById(networkId), now, request.agentVersion());
        report.wifi(request.ssid(), request.authentication(), request.band(), request.channel(), request.signalDbm());
        report.connection(request.latencyMs(), request.jitterMs(), request.packetLossPct(), request.gatewayLatencyMs());
        report.devices(deviceCount, newDevices);
        if (security != null) {
            report.security(security.get("score").asInt(), security.get("grade").asText());
        }
        if (quality != null) {
            report.quality(quality.get("score").asInt());
        }
        report.details(details.toString());
        reports.save(report);
        raiseAlerts(networkId, ssid, newDevices, seen.unauthorizedPresent(), security);

        String summary = measured ? summary(ssid, status)
                : "Sensor de «" + ssid + "»: reporte recibido, pero el computador no pudo medir la conexión.";
        return Optional.of(new AgentReportAck(measurement == null ? null : measurement.id(), deviceCount, newDevices,
                report.getSecurityScore(), report.getSecurityGrade(),
                status == null ? null : status != NetworkStatus.NORMAL, summary));
    }

    /**
     * New devices are announced once (the next report no longer sees them as new). A device marked as not authorized
     * and a critical security grade stay true report after report, so they raise an alert only while there is no
     * open one with the same message. An alert never breaks the report.
     */
    private void raiseAlerts(Long networkId, String ssid, int newDevices, int unauthorizedPresent, JsonNode security) {
        try {
            if (newDevices > 0) {
                alert(networkId, Alert.Severity.WARNING, newDevices == 1
                        ? "Apareció 1 equipo nuevo en «" + ssid + "»."
                        : "Aparecieron " + newDevices + " equipos nuevos en «" + ssid + "».", m -> false);
            }
            if (unauthorizedPresent > 0) {
                String message = unauthorizedPresent == 1
                        ? "Un equipo marcado como no autorizado está conectado a «" + ssid + "»."
                        : unauthorizedPresent + " equipos marcados como no autorizados están conectados a «" + ssid + "».";
                alert(networkId, Alert.Severity.WARNING, message, m -> m.contains("no autorizado"));
            }
            if (security != null && "CRITICAL".equals(security.path("grade").asText())) {
                String start = "Seguridad crítica en «" + ssid + "»";
                alert(networkId, Alert.Severity.CRITICAL,
                        start + ": " + security.path("summary").asText("revisa el cifrado y los puertos del router."),
                        m -> m.startsWith(start));
            }
        } catch (RuntimeException e) {
            log.warn("Could not raise the alerts of the sensor of network {}: {}", networkId, e.getMessage());
        }
    }

    private void alert(Long networkId, Alert.Severity severity, String message, Predicate<String> sameAsOpen) {
        boolean open = alerts.findByNetworkIdAndStatusNot(networkId, Alert.Status.RESOLVED).stream()
                .anyMatch(a -> sameAsOpen.test(a.getMessage()));
        if (!open) {
            String text = message.length() > 300 ? message.substring(0, 299) + "…" : message;
            alerts.save(new Alert(networks.getReferenceById(networkId), null, severity, text));
        }
    }

    static NetworkSnapshot snapshot(AgentReportRequest request, Instant at) {
        // Traffic is stored only when both counters came (the first report after installing has no previous one).
        Double trafficMb = request.trafficBytes() == null ? null
                : Math.round(request.trafficBytes() / BYTES_PER_MB * 1000) / 1000.0;
        Map<String, Long> protocols = new LinkedHashMap<>();
        request.protocolPacketsOrEmpty().forEach(p -> protocols.merge(p.protocol(), p.packets(), Long::sum));
        return new NetworkSnapshot(request.latencyMs(), request.jitterMs(), request.packetLossPct(),
                request.rxRateMbps(), request.signalDbm(), request.deviceCount(), trafficMb, request.trafficPackets(),
                protocols, at, false);
    }

    /** The same analysis the monitoring runs after each reading; a failure never loses the measurement. */
    private NetworkStatus analyze(Long networkId) {
        try {
            AnalysisResultResponse result = analysis.analyze(networkId, AnalysisType.THRESHOLD, null);
            return result.detectedStatus();
        } catch (RuntimeException e) {
            log.warn("Analysis of network {} after its sensor report skipped: {}", networkId, e.getMessage());
            return null;
        }
    }

    private static String summary(String ssid, NetworkStatus status) {
        String state = status == null ? "" : switch (status) {
            case NORMAL -> " Estado: normal.";
            case WARNING -> " Estado: advertencia.";
            case CRITICAL -> " Estado: crítico.";
        };
        return "Sensor de «" + ssid + "»: medición guardada." + state;
    }
}
