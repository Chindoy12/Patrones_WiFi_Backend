package com.wifisense.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CachingAiClientTest {

    private static final AiQualityRequest QUALITY = new AiQualityRequest(20.0, 2.0, 0.0, 100.0, null, null, -55.0);
    private static final AiQualityRequest OTHER_QUALITY = new AiQualityRequest(80.0, 9.0, 1.5, 100.0, null, null, -70.0);
    private static final AiNearbyRequest SCAN = new AiNearbyRequest(List.of(
            new AiNearbyRequest.AccessPoint("Casa", false, "WPA2", "CCMP", 6, "2.4", 70, -60, true, null)));

    /** A clock the test moves forward. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-11-09T15:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final ObjectMapper json = new ObjectMapper();
    private final AiAnalysisClient target = mock(AiAnalysisClient.class);
    private final MovableClock clock = new MovableClock();
    private final CachingAiClient proxy = new CachingAiClient(target, Duration.ofMinutes(10), 3, clock);

    private ObjectNode answer(int score) {
        return json.createObjectNode().put("score", score);
    }

    private static AiServiceUnavailableException unreachable() {
        return new AiServiceUnavailableException("Falló la solicitud al servicio de IA: timeout",
                new ResourceAccessException("I/O error: Read timed out"));
    }

    @Test
    void theSameQuestionIsAskedToTheAiOnlyOnce() {
        when(target.qualityScore(QUALITY)).thenReturn(answer(90));

        JsonNode first = proxy.qualityScore(QUALITY);
        JsonNode second = proxy.qualityScore(new AiQualityRequest(20.0, 2.0, 0.0, 100.0, null, null, -55.0));

        assertThat(first.get("score").asInt()).isEqualTo(90);
        assertThat(second).isEqualTo(first);
        verify(target, times(1)).qualityScore(any());
        assertThat(proxy.stats()).isEqualTo(new CachingAiClient.CacheStats(1, 1));
    }

    @Test
    void differentDataIsAnotherQuestion() {
        when(target.qualityScore(QUALITY)).thenReturn(answer(90));
        when(target.qualityScore(OTHER_QUALITY)).thenReturn(answer(40));

        assertThat(proxy.qualityScore(QUALITY).get("score").asInt()).isEqualTo(90);
        assertThat(proxy.qualityScore(OTHER_QUALITY).get("score").asInt()).isEqualTo(40);

        verify(target).qualityScore(QUALITY);
        verify(target).qualityScore(OTHER_QUALITY);
    }

    @Test
    void anAnswerIsKeptOnlyForItsTime() {
        when(target.nearbyAnalyze(SCAN)).thenReturn(answer(1), answer(2));

        proxy.nearbyAnalyze(SCAN);
        clock.advance(Duration.ofMinutes(9));
        assertThat(proxy.nearbyAnalyze(SCAN).get("score").asInt()).isEqualTo(1);
        clock.advance(Duration.ofMinutes(2));
        assertThat(proxy.nearbyAnalyze(SCAN).get("score").asInt()).isEqualTo(2);

        verify(target, times(2)).nearbyAnalyze(SCAN);
    }

    @Test
    void aFailureIsNeverKept() {
        when(target.qualityScore(QUALITY)).thenThrow(unreachable()).thenReturn(answer(90));

        assertThatThrownBy(() -> proxy.qualityScore(QUALITY)).isInstanceOf(AiServiceUnavailableException.class);
        assertThat(proxy.qualityScore(QUALITY).get("score").asInt()).isEqualTo(90);

        verify(target, times(2)).qualityScore(QUALITY);
    }

    @Test
    void theLeastRecentlyUsedAnswerLeavesWhenTheCacheIsFull() {
        AiChannelRequest one = new AiChannelRequest("2.4", 1, List.of());
        AiChannelRequest six = new AiChannelRequest("2.4", 6, List.of());
        AiChannelRequest eleven = new AiChannelRequest("2.4", 11, List.of());
        AiChannelRequest thirteen = new AiChannelRequest("2.4", 13, List.of());
        when(target.channelRecommend(any())).thenReturn(answer(0));

        proxy.channelRecommend(one);
        proxy.channelRecommend(six);
        proxy.channelRecommend(eleven);
        proxy.channelRecommend(one);       // used again: now six is the oldest
        proxy.channelRecommend(thirteen);  // the cache keeps 3: six leaves
        proxy.channelRecommend(one);
        proxy.channelRecommend(six);

        verify(target, times(1)).channelRecommend(one);
        verify(target, times(2)).channelRecommend(six);
    }

    @Test
    void changingAnAnswerDoesNotChangeWhatTheProxyKeeps() {
        when(target.securityAssess(any())).thenReturn(answer(70));
        AiSecurityRequest request = new AiSecurityRequest("WPA2", "CCMP", List.of(), 3, 0, "2.4", 6, 1);

        ((ObjectNode) proxy.securityAssess(request)).put("score", 0);

        assertThat(proxy.securityAssess(request).get("score").asInt()).isEqualTo(70);
    }

    @Test
    void newMeasurementsTheModelTrainingCapturesAndHealthAlwaysReachTheAi() {
        AiAnalysisRequest measurements = new AiAnalysisRequest(1L, false, List.of());
        AiTrafficRequest capture = new AiTrafficRequest(30, 100, 64_000, 0, 0, null, null, 10, 5, 3, 7, "TIME_LIMIT",
                null, List.of(), List.of(), List.of());
        when(target.modelInfo()).thenReturn(answer(1), answer(2));
        when(target.modelEvaluation()).thenReturn(Optional.empty(), Optional.of(answer(10)));
        when(target.trafficAnalyze(capture)).thenReturn(answer(5));
        when(target.trainModel(any())).thenReturn(answer(0));
        when(target.isHealthy()).thenReturn(true, false);

        proxy.detectAnomalies(measurements);
        proxy.detectAnomalies(measurements);
        // a redeployed AI starts with another model: the training service must see it at once to restore the active one
        assertThat(proxy.modelInfo().get("score").asInt()).isEqualTo(1);
        assertThat(proxy.modelInfo().get("score").asInt()).isEqualTo(2);
        assertThat(proxy.modelEvaluation()).isEmpty();
        assertThat(proxy.modelEvaluation()).isPresent();
        proxy.trafficAnalyze(capture);
        proxy.trafficAnalyze(capture);
        proxy.trainModel(json.createObjectNode());
        proxy.trainModel(json.createObjectNode());
        assertThat(proxy.isHealthy()).isTrue();
        assertThat(proxy.isHealthy()).isFalse();

        verify(target, times(2)).detectAnomalies(measurements);
        verify(target, times(2)).modelInfo();
        verify(target, times(2)).modelEvaluation();
        verify(target, times(2)).trafficAnalyze(capture);
        verify(target, times(2)).trainModel(any());
        verify(target, times(2)).isHealthy();
        assertThat(proxy.stats()).isEqualTo(new CachingAiClient.CacheStats(0, 0));
    }

    @Test
    void itNeedsAPositiveTimeAndRoomForOneAnswer() {
        assertThatThrownBy(() -> new CachingAiClient(target, Duration.ZERO, 10, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CachingAiClient(target, Duration.ofMinutes(1), 0, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
