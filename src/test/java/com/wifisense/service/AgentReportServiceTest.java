package com.wifisense.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifisense.analysis.*;
import com.wifisense.dto.AgentReportAck;
import com.wifisense.dto.AgentReportRequest;
import com.wifisense.model.*;
import com.wifisense.repository.AgentDeviceRepository;
import com.wifisense.repository.AgentKeyRepository;
import com.wifisense.repository.AgentReportRepository;
import com.wifisense.repository.HomeNetworkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AgentReportServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");
    private static final String KEY = "wsa_" + "k".repeat(43);
    private static final String PHONE = "aaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String LAPTOP = "bbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String STRANGER = "cccccccccccccccccccccccc";

    private final AgentKeyService keyService = mock(AgentKeyService.class);
    private final AgentReportThrottle throttle = mock(AgentReportThrottle.class);
    private final AgentKeyRepository keys = mock(AgentKeyRepository.class);
    private final AgentReportRepository reports = mock(AgentReportRepository.class);
    private final AgentDeviceRepository devices = mock(AgentDeviceRepository.class);
    private final HomeNetworkRepository networks = mock(HomeNetworkRepository.class);
    private final AiAnalysisClient ai = mock(AiAnalysisClient.class);
    private final AgentAlertService alerts = mock(AgentAlertService.class);
    private final DeviceSessionTracker sessions = mock(DeviceSessionTracker.class);
    private final AgentReportService service = new AgentReportService(keyService, throttle, keys, reports, devices,
            networks, ai, alerts, new TransactionTemplate(mock(PlatformTransactionManager.class)),
            new AgentProperties(Duration.ofSeconds(50), Duration.ofDays(30), Duration.ofMinutes(15), 20, 48), JSON,
            Clock.fixed(NOW, ZoneOffset.UTC), sessions);
    private final HomeNetwork casa = new HomeNetwork(mock(User.class), "Casa", null, null);
    private final AgentKey key = new AgentKey(casa, "f".repeat(64), "wsa_kkkk");

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(casa, "id", 7L);
        ReflectionTestUtils.setField(key, "id", 3L);
        when(keyService.authenticate(KEY)).thenReturn(key);
        when(networks.getReferenceById(7L)).thenReturn(casa);
        when(reports.save(any(AgentReport.class))).thenAnswer(invocation -> {
            AgentReport report = invocation.getArgument(0);
            ReflectionTestUtils.setField(report, "id", 99L);
            return report;
        });
        when(reports.findByNetworkIdOrderByReceivedAtDesc(eq(7L), any(Pageable.class))).thenReturn(List.of());
        when(ai.securityAssess(any())).thenReturn(JSON.readTree(
                "{\"score\":75,\"grade\":\"ACCEPTABLE\",\"gradeLabel\":\"Aceptable\",\"findings\":[]}"));
        when(ai.qualityScore(any())).thenReturn(JSON.readTree("{\"score\":88,\"grade\":\"EXCELLENT\"}"));
        when(ai.detectAnomalies(any())).thenReturn(anomaly(false, "NONE"));
    }

    private static AgentReportRequest request(List<String> hashes, Double loss) {
        return new AgentReportRequest("1.0.0", "Windows", "Casa", "WPA2", "CCMP", "5", 44, -58, 84, 433.3, 390.0, 2,
                9, null, 21.5, 2.0, loss, 3.1, hashes.size(), hashes, null, List.of(443, 80, 80), null);
    }

    private static AiAnalysisResponse anomaly(boolean detected, String type) {
        return new AiAnalysisResponse(detected, detected ? 0.7 : 0.2,
                detected ? AiPrediction.Severity.HIGH : AiPrediction.Severity.NONE, type,
                detected ? "Congestión" : null, "msg", detected ? "Reinicia el router" : null, "RULES", List.of(),
                Map.of(), detected ? 1 : 0, 1, List.of(), "isolation-forest-1.1", false);
    }

    private AgentReport saved() {
        ArgumentCaptor<AgentReport> captor = ArgumentCaptor.forClass(AgentReport.class);
        verify(reports).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void firstReportLearnsTheDevicesWithoutCallingThemNew() throws Exception {
        when(devices.existsByNetworkId(7L)).thenReturn(false);

        AgentReportAck ack = service.receive(KEY, request(List.of(PHONE, LAPTOP), 0.0));

        verify(devices, times(2)).save(any(AgentDevice.class));
        ArgumentCaptor<AiSecurityRequest> security = ArgumentCaptor.forClass(AiSecurityRequest.class);
        verify(ai).securityAssess(security.capture());
        assertThat(security.getValue().newDeviceCount()).isZero();
        assertThat(security.getValue().openPorts()).containsExactly(80, 443);
        assertThat(ack.deviceCount()).isEqualTo(2);
        assertThat(ack.newDeviceCount()).isZero();
        assertThat(ack.summary()).isEqualTo("2 equipos conectados. Seguridad: Aceptable (75/100).");

        AgentReport report = saved();
        assertThat(report.getSecurityScore()).isEqualTo(75);
        assertThat(report.getSecurityGrade()).isEqualTo("ACCEPTABLE");
        assertThat(report.getQualityScore()).isEqualTo(88);
        assertThat(report.getAnomalyDetected()).isFalse();
        assertThat(report.getAnomalyType()).isEqualTo("NONE");
        JsonNode details = JSON.readTree(report.getDetails());
        assertThat(details.path("openPorts").toString()).isEqualTo("[80,443]");
        assertThat(details.path("wifi").path("neighborsSameChannel").asInt()).isEqualTo(2);
        verify(keys).markSeen(3L, NOW);
    }

    @Test
    void aDeviceNeverSeenBeforeIsNewAndTheAiIsTold() {
        AgentDevice phone = new AgentDevice(7L, PHONE, NOW.minusSeconds(3600));
        when(devices.existsByNetworkId(7L)).thenReturn(true);
        when(devices.findByNetworkIdOrderByFirstSeenAtAsc(7L)).thenReturn(List.of(phone));

        AgentReportAck ack = service.receive(KEY, request(List.of(PHONE, STRANGER), 0.0));

        assertThat(ack.newDeviceCount()).isEqualTo(1);
        assertThat(ack.summary()).startsWith("2 equipos conectados (1 nuevo).");
        assertThat(phone.getLastSeenAt()).isEqualTo(NOW);
        ArgumentCaptor<AiSecurityRequest> security = ArgumentCaptor.forClass(AiSecurityRequest.class);
        verify(ai).securityAssess(security.capture());
        assertThat(security.getValue().newDeviceCount()).isEqualTo(1);
        assertThat(saved().getNewDeviceCount()).isEqualTo(1);
    }

    @Test
    void anomalyDetectionSeesThePreviousCompleteReportsInOrder() {
        AgentReport older = new AgentReport(casa, NOW.minusSeconds(600), "1.0.0");
        older.connection(20.0, 2.0, 0.0, null);
        AgentReport incomplete = new AgentReport(casa, NOW.minusSeconds(450), "1.0.0");
        AgentReport newer = new AgentReport(casa, NOW.minusSeconds(300), "1.0.0");
        newer.connection(25.0, 3.0, 1.0, null);
        when(reports.findByNetworkIdOrderByReceivedAtDesc(eq(7L), any(Pageable.class)))
                .thenReturn(List.of(newer, incomplete, older));
        when(ai.detectAnomalies(any())).thenReturn(anomaly(true, "CONGESTION"));

        AgentReportAck ack = service.receive(KEY, request(List.of(PHONE), 0.0));

        ArgumentCaptor<AiAnalysisRequest> sent = ArgumentCaptor.forClass(AiAnalysisRequest.class);
        verify(ai).detectAnomalies(sent.capture());
        assertThat(sent.getValue().simulatedData()).isFalse();
        assertThat(sent.getValue().measurements()).extracting(AiAnalysisRequest.Sample::latency)
                .containsExactly(20.0, 25.0, 21.5);
        assertThat(ack.anomalyDetected()).isTrue();
        assertThat(saved().getAnomalyType()).isEqualTo("CONGESTION");
    }

    @Test
    void withoutPacketLossThereIsNoAnomalyDetection() {
        service.receive(KEY, request(List.of(PHONE), null));

        verify(ai, never()).detectAnomalies(any());
        assertThat(saved().getAnomalyDetected()).isNull();
    }

    @Test
    void theReportIsKeptWhenTheAiIsDown() {
        when(ai.securityAssess(any())).thenThrow(new AiServiceUnavailableException("down", null));
        when(ai.qualityScore(any())).thenThrow(new AiServiceUnavailableException("down", null));
        when(ai.detectAnomalies(any())).thenThrow(new AiServiceUnavailableException("down", null));

        AgentReportAck ack = service.receive(KEY, request(List.of(PHONE), 0.0));

        AgentReport report = saved();
        assertThat(report.getSecurityScore()).isNull();
        assertThat(report.getQualityScore()).isNull();
        assertThat(report.getAnomalyDetected()).isNull();
        assertThat(ack.summary()).isEqualTo("1 equipo conectado.");
    }

    @Test
    void anUnexpectedSecurityAnswerIsIgnored() throws Exception {
        when(ai.securityAssess(any())).thenReturn(JSON.readTree("{\"score\":140,\"grade\":\"SUPER\"}"));

        service.receive(KEY, request(List.of(PHONE), 0.0));

        assertThat(saved().getSecurityGrade()).isNull();
    }

    @Test
    void nothingIsStoredForAnInvalidKeyOrAnAgentThatReportsTooOften() {
        when(keyService.authenticate("wsa_mala")).thenThrow(new InvalidAgentKeyException());
        doThrow(new AgentReportLimitException(30)).when(throttle).acquire(3L);

        assertThatThrownBy(() -> service.receive("wsa_mala", request(List.of(PHONE), 0.0)))
                .isInstanceOf(InvalidAgentKeyException.class);
        assertThatThrownBy(() -> service.receive(KEY, request(List.of(PHONE), 0.0)))
                .isInstanceOf(AgentReportLimitException.class);
        verifyNoInteractions(ai, reports, devices);
    }

    @Test
    void storesTheMakerAndNameOfANewDeviceFromTheReport() {
        when(devices.existsByNetworkId(7L)).thenReturn(true);
        when(devices.findByNetworkIdOrderByFirstSeenAtAsc(7L)).thenReturn(List.of());
        AgentReportRequest request = new AgentReportRequest("1.2.0", "Windows", "Casa", "WPA2", "CCMP", "5", 44, -58,
                84, null, null, null, null, null, 20.0, 2.0, 0.0, null, 1,
                null, List.of(new AgentReportRequest.DeviceInfo(PHONE, "Apple", "iPhone-de-Ana", null)), List.of(),
                null);

        service.receive(KEY, request);

        ArgumentCaptor<AgentDevice> stored = ArgumentCaptor.forClass(AgentDevice.class);
        verify(devices).save(stored.capture());
        assertThat(stored.getValue().getVendor()).isEqualTo("Apple");
        assertThat(stored.getValue().getHostname()).isEqualTo("iPhone-de-Ana");
        assertThat(stored.getValue().getFingerprint()).isEqualTo(PHONE);
    }

    @Test
    void storesWhatKindOfDeviceItIsAndWhichOnesAreTheComputerAndTheRouter() {
        AgentDevice known = new AgentDevice(7L, LAPTOP, NOW.minusSeconds(3600));
        when(devices.existsByNetworkId(7L)).thenReturn(true);
        when(devices.findByNetworkIdOrderByFirstSeenAtAsc(7L)).thenReturn(List.of(known));
        AgentReportRequest request = new AgentReportRequest("1.7.0", "Windows", "Casa", "WPA2", "CCMP", "5", 44, -58,
                84, null, null, null, null, null, 20.0, 2.0, 0.0, null, 3, null,
                List.of(new AgentReportRequest.DeviceInfo(PHONE, null, "iPhone de Ana", true, "iPhone15,2", "PHONE", null),
                        new AgentReportRequest.DeviceInfo(LAPTOP, "Lenovo", "PC-SALA", false, " ", "COMPUTER", "SELF"),
                        new AgentReportRequest.DeviceInfo(STRANGER, "TP-Link", null, false, null, null, "ROUTER")),
                List.of(), null);

        service.receive(KEY, request);

        ArgumentCaptor<AgentDevice> stored = ArgumentCaptor.forClass(AgentDevice.class);
        verify(devices, times(2)).save(stored.capture());
        AgentDevice phone = stored.getAllValues().stream().filter(d -> d.getFingerprint().equals(PHONE)).findFirst()
                .orElseThrow();
        assertThat(phone.getKind()).isEqualTo("PHONE");
        assertThat(phone.getModel()).isEqualTo("iPhone15,2");
        assertThat(phone.getRole()).isNull();
        assertThat(known.getRole()).isEqualTo("SELF");
        assertThat(known.getKind()).isEqualTo("COMPUTER");
        assertThat(known.getModel()).isNull();  // a blank model is ignored
        assertThat(stored.getAllValues()).filteredOn(d -> "ROUTER".equals(d.getRole()))
                .extracting(AgentDevice::getFingerprint).containsExactly(STRANGER);
    }

    @Test
    void whileTheUserIdentifiesADeviceTheAgentIsAskedToReportEveryMinute() {
        AgentReportAck usual = service.receive(KEY, request(List.of(PHONE), 0.0));
        assertThat(usual.nextReportSeconds()).isNull();

        org.springframework.test.util.ReflectionTestUtils.setField(key, "identifyUntil", NOW.plusSeconds(300));
        assertThat(service.receive(KEY, request(List.of(PHONE), 0.0)).nextReportSeconds()).isEqualTo(60);

        org.springframework.test.util.ReflectionTestUtils.setField(key, "identifyUntil", NOW.minusSeconds(1));
        assertThat(service.receive(KEY, request(List.of(PHONE), 0.0)).nextReportSeconds()).isNull();
    }

    @Test
    void aLongCipherIsTrimmedInsteadOfRejectedAndRaisesNoError() {
        ArgumentCaptor<AiSecurityRequest> security = ArgumentCaptor.forClass(AiSecurityRequest.class);
        AgentReportRequest request = new AgentReportRequest("1.2.0", "Windows", "Casa", "WPA2",
                "CCMP (TKIP) y un texto larguísimo que no cabe", "5", 44, -58, 84, null, null, null, null, null,
                20.0, 2.0, 0.0, null, 1, List.of(PHONE), null, List.of(), null);

        service.receive(KEY, request);

        verify(ai).securityAssess(security.capture());
        assertThat(security.getValue().cipher()).hasSize(20);
    }

    @Test
    void raisesAlertsForNewDevicesAndAnomalies() {
        when(devices.existsByNetworkId(7L)).thenReturn(true);
        when(devices.findByNetworkIdOrderByFirstSeenAtAsc(7L)).thenReturn(List.of());
        when(ai.detectAnomalies(any())).thenReturn(anomaly(true, "CONGESTION"));

        service.receive(KEY, request(List.of(PHONE, LAPTOP), 0.0));

        ArgumentCaptor<List<AgentAlertService.Signal>> raised = ArgumentCaptor.forClass(List.class);
        verify(alerts).process(eq(7L), raised.capture());
        assertThat(raised.getValue()).extracting(AgentAlertService.Signal::type)
                .contains(com.wifisense.model.AlertType.NEW_DEVICE, com.wifisense.model.AlertType.ANOMALY);
    }

    @Test
    void anUnauthorizedDeviceStillPresentRaisesAnAlert() {
        AgentDevice blocked = new AgentDevice(7L, PHONE, NOW.minusSeconds(3600));
        blocked.setAuthorized(false);
        when(devices.existsByNetworkId(7L)).thenReturn(true);
        when(devices.findByNetworkIdOrderByFirstSeenAtAsc(7L)).thenReturn(List.of(blocked));

        service.receive(KEY, request(List.of(PHONE), 0.0));

        ArgumentCaptor<List<AgentAlertService.Signal>> raised = ArgumentCaptor.forClass(List.class);
        verify(alerts).process(eq(7L), raised.capture());
        assertThat(raised.getValue()).extracting(AgentAlertService.Signal::type)
                .contains(com.wifisense.model.AlertType.UNAUTHORIZED_DEVICE);
    }

    private static AgentReportRequest withChannels(List<AgentReportRequest.ChannelCount> counts) {
        return new AgentReportRequest("1.3.0", "Windows", "Casa", "WPA2", "CCMP", "2.4", 6, -58, 84, null, null, 4,
                9, null, 21.5, 2.0, 0.0, 3.1, 1, List.of(PHONE), null, List.of(), counts);
    }

    @Test
    void remembersThatADeviceUsesAPrivateMac() {
        when(devices.existsByNetworkId(7L)).thenReturn(true);
        when(devices.findByNetworkIdOrderByFirstSeenAtAsc(7L)).thenReturn(List.of());
        AgentReportRequest request = new AgentReportRequest("1.3.0", "Windows", "Casa", "WPA2", "CCMP", "5", 44, -58,
                84, null, null, null, null, null, 20.0, 2.0, 0.0, null, 2, null,
                List.of(new AgentReportRequest.DeviceInfo(STRANGER, null, null, true),
                        new AgentReportRequest.DeviceInfo(LAPTOP, "Intel", "PC-SALA", null)), List.of(), null);

        service.receive(KEY, request);

        ArgumentCaptor<AgentDevice> stored = ArgumentCaptor.forClass(AgentDevice.class);
        verify(devices, times(2)).save(stored.capture());
        assertThat(stored.getAllValues()).filteredOn(AgentDevice::isPrivateMac)
                .extracting(AgentDevice::getFingerprint).containsExactly(STRANGER);
    }

    @Test
    void asksTheAiForTheBestChannelAndKeepsItWithTheReport() throws Exception {
        when(ai.channelRecommend(any())).thenReturn(JSON.readTree(
                "{\"recommendedChannel\":11,\"shouldChange\":true,\"summary\":\"Cambia al 11\"}"));
        ArgumentCaptor<AiChannelRequest> asked = ArgumentCaptor.forClass(AiChannelRequest.class);

        service.receive(KEY, withChannels(List.of(new AgentReportRequest.ChannelCount(6, 4),
                new AgentReportRequest.ChannelCount(1, 2))));

        verify(ai).channelRecommend(asked.capture());
        assertThat(asked.getValue().band()).isEqualTo("2.4");
        assertThat(asked.getValue().channel()).isEqualTo(6);
        assertThat(asked.getValue().channelCounts())
                .containsExactly(new AiChannelRequest.ChannelCount(6, 4), new AiChannelRequest.ChannelCount(1, 2));
        JsonNode channel = JSON.readTree(saved().getDetails()).get("channel");
        assertThat(channel.get("recommendedChannel").asInt()).isEqualTo(11);
        assertThat(channel.get("shouldChange").asBoolean()).isTrue();
    }

    @Test
    void withoutNeighbourCountsTheAiIsNotAskedForAChannel() throws Exception {
        service.receive(KEY, request(List.of(PHONE), 0.0));

        verify(ai, never()).channelRecommend(any());
        assertThat(JSON.readTree(saved().getDetails()).get("channel").isNull()).isTrue();
    }

    @Test
    void aChannelFailureOrOddAnswerDoesNotLoseTheReport() throws Exception {
        when(ai.channelRecommend(any())).thenThrow(new AiServiceUnavailableException("caído", null));

        service.receive(KEY, withChannels(List.of(new AgentReportRequest.ChannelCount(6, 4))));

        assertThat(JSON.readTree(saved().getDetails()).get("channel").isNull()).isTrue();
        assertThat(saved().getSecurityScore()).isEqualTo(75);
    }
}
