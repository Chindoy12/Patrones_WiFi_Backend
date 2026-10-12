package com.wifisense.analysis;

import com.wifisense.model.AiPrediction;
import com.wifisense.model.Measurement;
import com.wifisense.model.Network;
import com.wifisense.model.NetworkStatus;
import com.wifisense.model.TrafficObservation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static com.wifisense.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AnomalyDetectionStrategyTest {

    private final AiAnalysisClient client = mock(AiAnalysisClient.class);
    private final AnomalyDetectionStrategy strategy = new AnomalyDetectionStrategy(client);
    private final Network network = network(1, NetworkStatus.NORMAL);

    private static AiAnalysisResponse response(boolean detected, AiPrediction.Severity severity) {
        return new AiAnalysisResponse(detected, 0.7, severity, detected ? "CONGESTION" : "NONE", null, "msg", null,
                "RULES", List.of(), Map.of(), 1, 2, List.of(), "isolation-forest-1.1", true);
    }

    @Test
    void highSeverityMapsToCriticalAndKeepsTheAiResponse() {
        when(client.detectAnomalies(any())).thenReturn(response(true, AiPrediction.Severity.HIGH));
        AnalysisContext context = new AnalysisContext(1, "Test",
                List.of(measurement(network, 300, 80, 25, -88, NOW)), List.of());

        AnalysisOutcome outcome = strategy.analyze(context);

        assertThat(outcome.status()).isEqualTo(NetworkStatus.CRITICAL);
        assertThat(outcome.ai()).isPresent();
    }

    @Test
    void noAnomalyMapsToNormal() {
        when(client.detectAnomalies(any())).thenReturn(response(false, AiPrediction.Severity.NONE));
        AnalysisContext context = new AnalysisContext(1, "Test",
                List.of(measurement(network, 20, 3, 0.2, -55, NOW)), List.of());

        assertThat(strategy.analyze(context).status()).isEqualTo(NetworkStatus.NORMAL);
    }

    @Test
    void requestCarriesTrafficMatchedByTimestampAndSimulatedFlag() {
        when(client.detectAnomalies(any())).thenReturn(response(false, AiPrediction.Severity.NONE));
        Measurement measurement = measurement(network, 20, 3, 0.2, -55, NOW);
        TrafficObservation traffic = new TrafficObservation(network, 512.0, 300_000L, NOW);

        strategy.analyze(new AnalysisContext(1, "Test", List.of(measurement), List.of(traffic)));

        ArgumentCaptor<AiAnalysisRequest> captor = ArgumentCaptor.forClass(AiAnalysisRequest.class);
        verify(client).detectAnomalies(captor.capture());
        AiAnalysisRequest request = captor.getValue();
        assertThat(request.simulatedData()).isTrue();
        assertThat(request.measurements().getFirst().trafficVolume()).isEqualTo(512.0);
        assertThat(request.measurements().getFirst().packetCount()).isEqualTo(300_000L);
    }
}
