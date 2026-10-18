package com.wifisense.service;

import com.wifisense.analysis.AnalysisType;
import com.wifisense.analysis.NetworkAnalysisFacade;
import com.wifisense.dto.AgentReportAck;
import com.wifisense.dto.AgentReportRequest;
import com.wifisense.dto.AnalysisResultResponse;
import com.wifisense.dto.MeasurementResponse;
import com.wifisense.model.NetworkSensor;
import com.wifisense.model.NetworkStatus;
import com.wifisense.network.DataSourceType;
import com.wifisense.network.NetworkSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reports of the sensor of a team network. Each one becomes a real measurement of that network (latency, jitter and
 * loss, signal, link rate and connected devices) plus the traffic of the sensor's computer and its packets per
 * protocol, and then the same threshold analysis the monitoring runs: alerts and the network state react as usual.
 * The devices are only counted: the team panel never receives who is connected.
 */
@Service
public class SensorReportService {

    private static final Logger log = LoggerFactory.getLogger(SensorReportService.class);
    private static final double BYTES_PER_MB = 1024.0 * 1024.0;

    private final NetworkSensorService sensors;
    private final AgentReportThrottle throttle;
    private final MeasurementCollectionService collection;
    private final NetworkAnalysisFacade analysis;
    private final Clock clock;

    public SensorReportService(NetworkSensorService sensors, AgentReportThrottle throttle,
                               MeasurementCollectionService collection, NetworkAnalysisFacade analysis, Clock clock) {
        this.sensors = sensors;
        this.throttle = throttle;
        this.collection = collection;
        this.analysis = analysis;
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

        if (request.latencyMs() == null || request.jitterMs() == null || request.packetLossPct() == null) {
            return Optional.of(new AgentReportAck(null, request.deviceCount(), 0, null, null, null,
                    "Sensor de «" + ssid + "»: reporte recibido, pero el computador no pudo medir la conexión."));
        }
        MeasurementResponse measurement = collection.record(networkId, snapshot(request, now), DataSourceType.AGENT);
        NetworkStatus status = analyze(networkId);
        return Optional.of(new AgentReportAck(measurement.id(), request.deviceCount(), 0, null, null,
                status == null ? null : status != NetworkStatus.NORMAL, summary(ssid, status)));
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
