package com.wifisense.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifisense.TestData;
import com.wifisense.analysis.AiAnalysisClient;
import com.wifisense.analysis.AiServiceUnavailableException;
import com.wifisense.analysis.AnalysisType;
import com.wifisense.analysis.NetworkAnalysisFacade;
import com.wifisense.dto.AgentReportAck;
import com.wifisense.dto.AgentReportRequest;
import com.wifisense.dto.AnalysisResultResponse;
import com.wifisense.dto.MeasurementResponse;
import com.wifisense.model.Alert;
import com.wifisense.model.Network;
import com.wifisense.model.NetworkSensor;
import com.wifisense.model.NetworkStatus;
import com.wifisense.model.SensorReport;
import com.wifisense.network.DataSourceType;
import com.wifisense.network.NetworkSnapshot;
import com.wifisense.repository.AlertRepository;
import com.wifisense.repository.NetworkRepository;
import com.wifisense.repository.SensorReportRepository;
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
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PHONE = "aaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String LAPTOP = "bbbbbbbbbbbbbbbbbbbbbbbb";

    private final NetworkSensorService sensors = mock(NetworkSensorService.class);
    private final AgentReportThrottle throttle = mock(AgentReportThrottle.class);
    private final MeasurementCollectionService collection = mock(MeasurementCollectionService.class);
    private final NetworkAnalysisFacade analysis = mock(NetworkAnalysisFacade.class);
    private final SensorDeviceService devices = mock(SensorDeviceService.class);
    private final SensorReportRepository reports = mock(SensorReportRepository.class);
    private final NetworkRepository networks = mock(NetworkRepository.class);
    private final AlertRepository alerts = mock(AlertRepository.class);
    private final AiAnalysisClient ai = mock(AiAnalysisClient.class);
    private final SensorReportService service = new SensorReportService(sensors, throttle, collection, analysis,
            devices, reports, networks, alerts, ai, JSON, Clock.fixed(NOW, ZoneOffset.UTC));
    private final Network library = TestData.network(5L, NetworkStatus.NORMAL);
    private final NetworkSensor sensor = new NetworkSensor(library, "a".repeat(64), "wsa_ssss");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sensor, "id", 8L);
        when(sensors.authenticate(KEY)).thenReturn(Optional.of(sensor));
        when(networks.getReferenceById(5L)).thenReturn(library);
        when(devices.register(eq(5L), any(), eq(NOW))).thenReturn(new SensorDeviceService.Outcome(0, 0));
        when(collection.record(eq(5L), any(), eq(DataSourceType.AGENT))).thenReturn(new MeasurementResponse(40L, 5L,
                21.5, 2.0, 0.0, 433.3, -58, 6, DataSourceType.AGENT, false, NOW));
        when(ai.securityAssess(any())).thenThrow(new AiServiceUnavailableException("IA caída", null));
        when(ai.qualityScore(any())).thenThrow(new AiServiceUnavailableException("IA caída", null));
    }

    private static AgentReportRequest report(Double latency, Long bytes, Long packets,
                                             List<AgentReportRequest.ProtocolCount> protocols) {
        return new AgentReportRequest("1.5.0", "Windows", "WiFiSense-Test", "WPA3", "CCMP", "5", 36, -58, 84, 433.3,
                390.0, 1, 7, null, latency, 2.0, 0.0, 3.1, 6, List.of(PHONE, LAPTOP), null, List.of(80), null, bytes,
                packets, protocols);
    }

    private static AnalysisResultResponse analysis(NetworkStatus status) {
        return new AnalysisResultResponse(1L, 5L, "WiFiSense-Test", AnalysisType.THRESHOLD, status, status, 0.9, "ok",
                1, null, NOW, null);
    }

    private SensorReport savedReport() {
        ArgumentCaptor<SensorReport> saved = ArgumentCaptor.forClass(SensorReport.class);
        verify(reports).save(saved.capture());
        return saved.getValue();
    }

    private List<Alert> savedAlerts() {
        ArgumentCaptor<Alert> saved = ArgumentCaptor.forClass(Alert.class);
        verify(alerts, atLeast(0)).save(saved.capture());
        return saved.getAllValues();
    }

    @Test
    void aKeyThatIsNotOfASensorIsLeftForMiWiFi() {
        assertThat(service.receive("wsa_" + "h".repeat(43), report(21.5, null, null, null))).isEmpty();
        verifyNoInteractions(collection, analysis, throttle, devices, reports);
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
        verify(sensors).markSeen(8L, NOW, "1.5.0", "Windows", "WiFiSense-Test");
    }

    @Test
    void theReportKeepsTheDevicesAndWhatTheAiSaysAboutSecurityQualityAndChannel() throws Exception {
        reset(ai);
        when(ai.securityAssess(any())).thenReturn(JSON.readTree(
                "{\"score\": 74, \"grade\": \"ACCEPTABLE\", \"summary\": \"Bien\", \"findings\": []}"));
        when(ai.qualityScore(any())).thenReturn(JSON.readTree("{\"score\": 88}"));
        when(devices.register(eq(5L), any(), eq(NOW))).thenReturn(new SensorDeviceService.Outcome(1, 0));

        AgentReportAck ack = service.receive(KEY, report(21.5, null, null, null)).orElseThrow();

        SensorReport saved = savedReport();
        assertThat(saved.getDeviceCount()).isEqualTo(6);
        assertThat(saved.getNewDeviceCount()).isEqualTo(1);
        assertThat(saved.getSecurityScore()).isEqualTo(74);
        assertThat(saved.getSecurityGrade()).isEqualTo("ACCEPTABLE");
        assertThat(saved.getQualityScore()).isEqualTo(88);
        assertThat(saved.getSsid()).isEqualTo("WiFiSense-Test");
        assertThat(JSON.readTree(saved.getDetails()).path("openPorts").toString()).isEqualTo("[80]");
        assertThat(JSON.readTree(saved.getDetails()).path("security").path("summary").asText()).isEqualTo("Bien");
        assertThat(ack.newDeviceCount()).isEqualTo(1);
        assertThat(ack.securityGrade()).isEqualTo("ACCEPTABLE");
        // a new device is announced to the team
        assertThat(savedAlerts()).singleElement().satisfies(alert -> {
            assertThat(alert.getMessage()).isEqualTo("Apareció 1 equipo nuevo en «WiFiSense-Test».");
            assertThat(alert.getSeverity()).isEqualTo(Alert.Severity.WARNING);
        });
    }

    @Test
    void aCriticalSecurityGradeRaisesOneAlertWhileItStaysOpen() throws Exception {
        reset(ai);
        when(ai.securityAssess(any())).thenReturn(JSON.readTree(
                "{\"score\": 20, \"grade\": \"CRITICAL\", \"summary\": \"El router tiene Telnet abierto.\"}"));
        when(ai.qualityScore(any())).thenThrow(new AiServiceUnavailableException("IA caída", null));

        service.receive(KEY, report(21.5, null, null, null));

        assertThat(savedAlerts()).singleElement().satisfies(alert -> {
            assertThat(alert.getSeverity()).isEqualTo(Alert.Severity.CRITICAL);
            assertThat(alert.getMessage())
                    .isEqualTo("Seguridad crítica en «WiFiSense-Test»: El router tiene Telnet abierto.");
        });

        Alert open = new Alert(library, null, Alert.Severity.CRITICAL, "Seguridad crítica en «WiFiSense-Test»: antes");
        when(alerts.findByNetworkIdAndStatusNot(5L, Alert.Status.RESOLVED)).thenReturn(List.of(open));
        clearInvocations(alerts);
        service.receive(KEY, report(21.5, null, null, null));
        verify(alerts, never()).save(any());
    }

    @Test
    void aDeviceMarkedAsNotAuthorizedThatIsConnectedIsAnAlert() {
        when(devices.register(eq(5L), any(), eq(NOW))).thenReturn(new SensorDeviceService.Outcome(0, 1));

        service.receive(KEY, report(21.5, null, null, null));

        assertThat(savedAlerts()).singleElement().extracting(Alert::getMessage)
                .isEqualTo("Un equipo marcado como no autorizado está conectado a «WiFiSense-Test».");
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
    void withoutAConnectionMeasurementTheDevicesAndTheAuditAreStillKept() {
        AgentReportAck ack = service.receive(KEY, report(null, null, null, null)).orElseThrow();

        verifyNoInteractions(collection, analysis);
        verify(devices).register(eq(5L), any(), eq(NOW));
        verify(sensors).markSeen(8L, NOW, "1.5.0", "Windows", "WiFiSense-Test");
        assertThat(savedReport().getLatencyMs()).isNull();
        assertThat(ack.reportId()).isNull();
        assertThat(ack.summary()).contains("no pudo medir la conexión");
    }

    @Test
    void aFailedAnalysisOrAnAiThatIsDownNeverLosesTheMeasurement() {
        when(analysis.analyze(any(), any(), any())).thenThrow(new IllegalStateException("sin datos"));

        AgentReportAck ack = service.receive(KEY, report(21.5, null, null, null)).orElseThrow();

        assertThat(ack.reportId()).isEqualTo(40L);
        assertThat(ack.anomalyDetected()).isNull();
        assertThat(ack.summary()).isEqualTo("Sensor de «WiFiSense-Test»: medición guardada.");
        SensorReport saved = savedReport();
        assertThat(saved.getSecurityScore()).isNull();
        assertThat(saved.getQualityScore()).isNull();
    }

    @Test
    void aComputerOnAnotherWifiDoesNotStoreItsMeasurementsAsThisNetworks() {
        AgentReportRequest elsewhere = new AgentReportRequest("1.6.0", "Windows", "Casa-5G", "WPA2", null, "5", 36,
                -50, null, null, null, null, null, null, 15.0, 1.0, 0.0, null, 3, List.of(PHONE), null, null, null);

        AgentReportAck ack = service.receive(KEY, elsewhere).orElseThrow();

        verify(sensors).markSeen(8L, NOW, "1.6.0", "Windows", "Casa-5G");
        verifyNoInteractions(collection, analysis, devices, reports, alerts);
        assertThat(ack.reportId()).isNull();
        assertThat(ack.summary()).startsWith("Este computador está conectado a «Casa-5G», no a «WiFiSense-Test»");
    }
}
