package com.wifisense.service;

import com.wifisense.TestData;
import com.wifisense.analysis.AnalysisType;
import com.wifisense.analysis.NetworkAnalysisFacade;
import com.wifisense.dto.AgentReportAck;
import com.wifisense.dto.AgentReportRequest;
import com.wifisense.dto.AnalysisResultResponse;
import com.wifisense.dto.MeasurementResponse;
import com.wifisense.model.Network;
import com.wifisense.model.NetworkSensor;
import com.wifisense.model.NetworkStatus;
import com.wifisense.network.DataSourceType;
import com.wifisense.network.NetworkSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SensorReportServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-18T10:05:00Z");
    private static final String KEY = "wsa_" + "s".repeat(43);

    private final NetworkSensorService sensors = mock(NetworkSensorService.class);
    private final AgentReportThrottle throttle = mock(AgentReportThrottle.class);
    private final MeasurementCollectionService collection = mock(MeasurementCollectionService.class);
    private final NetworkAnalysisFacade analysis = mock(NetworkAnalysisFacade.class);
    private final SensorReportService service = new SensorReportService(sensors, throttle, collection, analysis,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final Network library = TestData.network(5L, NetworkStatus.NORMAL);
    private final NetworkSensor sensor = new NetworkSensor(library, "a".repeat(64), "wsa_ssss");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sensor, "id", 8L);
        when(sensors.authenticate(KEY)).thenReturn(Optional.of(sensor));
        when(collection.record(eq(5L), any(), eq(DataSourceType.AGENT))).thenReturn(new MeasurementResponse(40L, 5L,
                21.5, 2.0, 0.0, 433.3, -58, 6, DataSourceType.AGENT, false, NOW));
    }

    private static AgentReportRequest report(Double latency, Long bytes, Long packets,
                                             List<AgentReportRequest.ProtocolCount> protocols) {
        return new AgentReportRequest("1.4.0", "Windows", "WiFiSense-Test", "WPA3", "CCMP", "5", 36, -58, 84, 433.3,
                390.0, 1, 7, null, latency, 2.0, 0.0, 3.1, 6, null, null, List.of(), null, bytes, packets, protocols);
    }

    private static AnalysisResultResponse analysis(NetworkStatus status) {
        return new AnalysisResultResponse(1L, 5L, "WiFiSense-Test", AnalysisType.THRESHOLD, status, status, 0.9, "ok",
                1, null, NOW, null);
    }

    @Test
    void aKeyThatIsNotOfASensorIsLeftForMiWiFi() {
        assertThat(service.receive("wsa_" + "h".repeat(43), report(21.5, null, null, null))).isEmpty();
        verifyNoInteractions(collection, analysis, throttle);
    }

    @Test
    void eachReportIsARealMeasurementWithTrafficAndProtocolsOfTheNetwork() {
        when(analysis.analyze(5L, AnalysisType.THRESHOLD, null)).thenReturn(analysis(NetworkStatus.NORMAL));

        AgentReportAck ack = service.receive(KEY, report(21.5, 5_242_880L, 4_100L, List.of(
                new AgentReportRequest.ProtocolCount("TCP", 3_000L), new AgentReportRequest.ProtocolCount("UDP", 900L),
                new AgentReportRequest.ProtocolCount("TCP", 100L)))).orElseThrow();

        ArgumentCaptor<NetworkSnapshot> snapshot = ArgumentCaptor.forClass(NetworkSnapshot.class);
        verify(collection).record(eq(5L), snapshot.capture(), eq(DataSourceType.AGENT));
        NetworkSnapshot stored = snapshot.getValue();
        assertThat(stored.latencyMs()).isEqualTo(21.5);
        assertThat(stored.signalStrengthDbm()).isEqualTo(-58);
        assertThat(stored.bandwidthMbps()).isEqualTo(433.3);
        assertThat(stored.connectedDevices()).isEqualTo(6);
        assertThat(stored.trafficVolumeMb()).isEqualTo(5.0);
        assertThat(stored.packetCount()).isEqualTo(4_100L);
        assertThat(stored.protocolPackets()).isEqualTo(Map.of("TCP", 3_100L, "UDP", 900L));
        assertThat(stored.simulated()).isFalse();
        assertThat(stored.collectedAt()).isEqualTo(NOW);

        assertThat(ack.reportId()).isEqualTo(40L);
        assertThat(ack.anomalyDetected()).isFalse();
        assertThat(ack.summary()).isEqualTo("Sensor de «WiFiSense-Test»: medición guardada. Estado: normal.");
        verify(throttle).acquireSensor(8L);
        verify(sensors).markSeen(8L, NOW, "1.4.0", "Windows");
    }

    @Test
    void theFirstReportHasNoTrafficYetAndAProblemIsReported() {
        when(analysis.analyze(5L, AnalysisType.THRESHOLD, null)).thenReturn(analysis(NetworkStatus.CRITICAL));

        AgentReportAck ack = service.receive(KEY, report(180.0, null, null, null)).orElseThrow();

        ArgumentCaptor<NetworkSnapshot> snapshot = ArgumentCaptor.forClass(NetworkSnapshot.class);
        verify(collection).record(eq(5L), snapshot.capture(), eq(DataSourceType.AGENT));
        assertThat(snapshot.getValue().hasTraffic()).isFalse();
        assertThat(snapshot.getValue().protocolPackets()).isEmpty();
        assertThat(ack.anomalyDetected()).isTrue();
        assertThat(ack.summary()).endsWith("Estado: crítico.");
    }

    @Test
    void withoutAConnectionMeasurementNothingIsStoredButTheSensorIsSeen() {
        AgentReportAck ack = service.receive(KEY, report(null, null, null, null)).orElseThrow();

        verifyNoInteractions(collection, analysis);
        verify(sensors).markSeen(8L, NOW, "1.4.0", "Windows");
        assertThat(ack.reportId()).isNull();
        assertThat(ack.summary()).contains("no pudo medir la conexión");
    }

    @Test
    void aFailedAnalysisNeverLosesTheMeasurement() {
        when(analysis.analyze(any(), any(), any())).thenThrow(new IllegalStateException("sin datos"));

        AgentReportAck ack = service.receive(KEY, report(21.5, null, null, null)).orElseThrow();

        assertThat(ack.reportId()).isEqualTo(40L);
        assertThat(ack.anomalyDetected()).isNull();
        assertThat(ack.summary()).isEqualTo("Sensor de «WiFiSense-Test»: medición guardada.");
    }
}
