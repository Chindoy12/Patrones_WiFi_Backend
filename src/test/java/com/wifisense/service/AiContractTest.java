package com.wifisense.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifisense.analysis.*;
import com.wifisense.dto.AgentReportRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Contract with the AI service (WiFiSense-ai). The JSON examples in src/test/resources/contracts are the same files the
 * AI tests use in tests/contracts: there the service proves it accepts these requests and answers these fields; here
 * the backend proves it sends exactly these requests and that the code that reads the answers accepts them. A renamed
 * field on either side breaks a test instead of production.
 */
class AiContractTest {

    /** The ObjectMapper Spring Boot builds (the one the HTTP client to the AI uses): dates as ISO text, not numbers. */
    private static final ObjectMapper BOOT_JSON = bootObjectMapper();

    private final ObjectMapper json = BOOT_JSON;

    private static ObjectMapper bootObjectMapper() {
        AtomicReference<ObjectMapper> mapper = new AtomicReference<>();
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .run(context -> mapper.set(context.getBean(ObjectMapper.class)));
        return mapper.get();
    }
    private final AiAnalysisClient ai = mock(AiAnalysisClient.class);
    private final AgentInsights insights = new AgentInsights(ai, json);

    /** Numbers are equal by value (an int 6200 and a long 6200 are the same JSON). */
    private static final Comparator<JsonNode> SAME_JSON = (a, b) -> a.isNumber() && b.isNumber()
            ? Double.compare(a.doubleValue(), b.doubleValue()) : a.equals(b) ? 0 : 1;

    private JsonNode contract(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/contracts/" + name)) {
            assertThat(in).as("contracts/" + name).isNotNull();
            return json.readTree(in);
        }
    }

    private void assertSameJson(Object sent, String contract) throws IOException {
        JsonNode actual = json.valueToTree(sent);
        JsonNode expected = contract(contract);
        assertThat(actual.equals(SAME_JSON, expected))
                .as("lo que envía el backend:%n%s%ndebe ser igual al contrato %s:%n%s", actual, contract, expected)
                .isTrue();
    }

    /** The agent report that produces exactly the requests of the contract examples. */
    private static AgentReportRequest report() {
        return new AgentReportRequest("1.7.0", "Windows", "Casa", "WPA2", "CCMP", "2.4", 6, -58, 80, null, null, 4, 13,
                null, 38.5, 4.2, 0.4, 2.0, 9, null, null, List.of(80, 23, 53, 23),
                List.of(new AgentReportRequest.ChannelCount(1, 2), new AgentReportRequest.ChannelCount(6, 7),
                        new AgentReportRequest.ChannelCount(11, 3)));
    }

    @Test
    void theSecurityAuditIsAskedAsTheAiExpectsAndItsAnswerIsAccepted() throws IOException {
        when(ai.securityAssess(any())).thenReturn(contract("security_response.json"));
        ArgumentCaptor<AiSecurityRequest> sent = ArgumentCaptor.forClass(AiSecurityRequest.class);

        JsonNode accepted = insights.assessSecurity(report(), 9, 1);

        verify(ai).securityAssess(sent.capture());
        assertSameJson(sent.getValue(), "security_request.json");
        assertThat(accepted).as("AgentInsights acepta la respuesta del contrato").isNotNull();
        assertThat(accepted.path("findings").get(0).has("severity")).isTrue();
    }

    @Test
    void theQualityScoreIsAskedAsTheAiExpectsAndItsAnswerIsAccepted() throws IOException {
        when(ai.qualityScore(any())).thenReturn(contract("quality_response.json"));
        ArgumentCaptor<AiQualityRequest> sent = ArgumentCaptor.forClass(AiQualityRequest.class);

        JsonNode accepted = insights.scoreQuality(report());

        verify(ai).qualityScore(sent.capture());
        assertSameJson(sent.getValue(), "quality_request.json");
        assertThat(accepted).isNotNull();
    }

    @Test
    void theBestChannelIsAskedAsTheAiExpectsAndItsAnswerIsAccepted() throws IOException {
        when(ai.channelRecommend(any())).thenReturn(contract("channel_response.json"));
        ArgumentCaptor<AiChannelRequest> sent = ArgumentCaptor.forClass(AiChannelRequest.class);

        JsonNode accepted = insights.recommendChannel(report());

        verify(ai).channelRecommend(sent.capture());
        assertSameJson(sent.getValue(), "channel_request.json");
        assertThat(accepted).isNotNull();
    }

    @Test
    void theAnomalyWindowTravelsWithTheNamesTypesAndDateFormatOfTheContract() throws IOException {
        List<AiAnalysisRequest.Sample> samples = new ArrayList<>();
        for (JsonNode m : contract("anomalies_request.json").path("measurements")) {
            samples.add(new AiAnalysisRequest.Sample(m.path("latency").asDouble(), m.path("jitter").asDouble(),
                    m.path("packetLoss").asDouble(), m.path("bandwidth").isNull() ? null : m.path("bandwidth").asDouble(),
                    m.path("signalStrength").asInt(),
                    m.path("trafficVolume").isNull() ? null : m.path("trafficVolume").asDouble(),
                    m.path("connectedDevices").asInt(),
                    m.path("packetCount").isNull() ? null : m.path("packetCount").asLong(),
                    Instant.parse(m.path("timestamp").asText())));
        }

        assertSameJson(new AiAnalysisRequest(3, false, samples), "anomalies_request.json");
    }

    @Test
    void theAnomalyAnswerHasEveryFieldTheBackendReadsAndParses() throws IOException {
        JsonNode answer = contract("anomalies_response.json");
        for (RecordComponent field : AiAnalysisResponse.class.getRecordComponents()) {
            assertThat(answer.has(field.getName())).as("la IA responde %s", field.getName()).isTrue();
        }

        AiAnalysisResponse parsed = json.treeToValue(answer, AiAnalysisResponse.class);

        assertThat(parsed.anomalyDetected()).isTrue();
        assertThat(parsed.severity()).isNotNull();
        assertThat(com.wifisense.model.AnomalyType.fromAi(parsed.anomalyType(), parsed.anomalyDetected()))
                .isNotEqualTo(com.wifisense.model.AnomalyType.NONE);
    }

    @Test
    void theModelDescriptionHasTheVersionTheTrainingSyncCompares() throws IOException {
        JsonNode model = contract("model_response.json");

        assertThat(model.path("modelVersion").isTextual()).isTrue();
        assertThat(model.path("trainedOn").isTextual()).isTrue();
        assertThat(model.path("realData").isBoolean()).isTrue();
    }

    @Test
    void theStatisticsOfACaptureTravelAsTheAiExpectsAndItsFactsAndInferencesAreRead() throws IOException {
        com.wifisense.repository.MeasurementRepository measurements =
                mock(com.wifisense.repository.MeasurementRepository.class);
        com.wifisense.model.Measurement latest = mock(com.wifisense.model.Measurement.class);
        when(latest.getPacketLossPct()).thenReturn(0.4);
        when(measurements.findByNetworkIdOrderByMeasuredAtDesc(any(), any())).thenReturn(List.of(latest));
        CaptureAnalysisService analysis = new CaptureAnalysisService(null, measurements, new CaptureFindingRules(), ai,
                null);
        com.wifisense.model.TrafficCapture capture = CaptureFixtures.running(CaptureFixtures.network(4), 7L,
                CaptureFixtures.NOW);
        new CaptureRecorder().apply(capture, CaptureFixtures.completed(7L), CaptureFixtures.NOW.plusSeconds(31));

        assertSameJson(analysis.request(capture), "traffic_request.json");
        List<com.wifisense.model.CaptureFinding> read = CaptureAnalysisService.parse(contract("traffic_response.json"));
        assertThat(read).extracting(com.wifisense.model.CaptureFinding::getCode)
                .contains("TRAFFIC_VOLUME", "CONNECTION_LOSS");
    }

    @Test
    void theAccessPointsOfAScanTravelAsTheAiExpectsAndItsReadingIsKeptApart() throws IOException {
        List<com.wifisense.model.WifiScanObservation> seen = List.of(
                seen("Casa de Ana", false, "WPA2", "CCMP", 6, 35, -78, true, "Huawei"),
                seen("Casa de Ana", false, "OPEN", null, 6, 60, -61, false, "TP-Link"),
                seen("Vecino 2.4", false, "WPA2", "CCMP", 6, 50, null, false, null),
                seen("Vecino B", false, "WPA2", "CCMP", 6, 44, null, false, null),
                seen(null, true, "WPA2", "CCMP", 1, 20, null, false, null));

        assertSameJson(NearbyAnalysisService.request(seen), "nearby_request.json");
        com.wifisense.model.WifiScanSession scan = mock(com.wifisense.model.WifiScanSession.class);
        com.wifisense.dto.NearbyAnalysisResponse read = NearbyAnalysisService.read(scan, contract("nearby_response.json"));
        assertThat(read.facts()).isNotEmpty().allMatch(item -> item.confidence() == null);
        assertThat(read.inferences()).isNotEmpty().allMatch(item -> item.confidence() != null);
        assertThat(read.uncertainty()).isNotBlank();
    }

    private static com.wifisense.model.WifiScanObservation seen(String ssid, boolean hidden, String authentication,
                                                               String cipher, int channel, int signal, Integer dbm,
                                                               boolean connected, String vendor) {
        com.wifisense.model.WifiScanObservation o = mock(com.wifisense.model.WifiScanObservation.class,
                RETURNS_DEEP_STUBS);
        when(o.getSsid()).thenReturn(ssid);
        when(o.getAccessPoint().isHidden()).thenReturn(hidden);
        when(o.getAccessPoint().getVendor()).thenReturn(vendor);
        when(o.getAuthentication()).thenReturn(authentication);
        when(o.getCipher()).thenReturn(cipher);
        when(o.getChannel()).thenReturn(channel);
        when(o.getBand()).thenReturn("2.4");
        when(o.getSignalPercent()).thenReturn(signal);
        when(o.getSignalDbm()).thenReturn(dbm);
        when(o.isConnected()).thenReturn(connected);
        return o;
    }
}
