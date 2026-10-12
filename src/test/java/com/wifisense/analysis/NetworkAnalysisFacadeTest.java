package com.wifisense.analysis;

import com.wifisense.event.NetworkEvent;
import com.wifisense.event.NetworkEventPublisher;
import com.wifisense.event.NetworkStatusChangedEvent;
import com.wifisense.model.AiPrediction;
import com.wifisense.model.AnalysisResult;
import com.wifisense.model.AnomalyType;
import com.wifisense.model.Network;
import com.wifisense.model.NetworkStatus;
import com.wifisense.repository.*;
import com.wifisense.service.MeasurementCollectionService;
import com.wifisense.service.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.wifisense.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NetworkAnalysisFacadeTest {

    private final NetworkRepository networks = mock(NetworkRepository.class);
    private final MeasurementRepository measurements = mock(MeasurementRepository.class);
    private final TrafficObservationRepository traffic = mock(TrafficObservationRepository.class);
    private final MeasurementCollectionService collection = mock(MeasurementCollectionService.class);
    private final AnalysisResultRepository results = mock(AnalysisResultRepository.class);
    private final AiPredictionRepository predictions = mock(AiPredictionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NetworkEventPublisher events = mock(NetworkEventPublisher.class);
    private final AnalysisStrategy strategy = mock(AnalysisStrategy.class);

    private NetworkAnalysisFacade facade;
    private Network network;

    @BeforeEach
    void setUp() {
        when(strategy.type()).thenReturn(AnalysisType.THRESHOLD);
        facade = new NetworkAnalysisFacade(List.of(strategy), networks, measurements, traffic, collection, results,
                predictions, users, events, new AnalysisProperties(50, null), Clock.fixed(NOW, ZoneOffset.UTC));
        network = network(1, NetworkStatus.NORMAL);
        when(networks.findWithZoneById(1L)).thenReturn(Optional.of(network));
        when(measurements.findByNetworkIdOrderByMeasuredAtDesc(eq(1L), any()))
                .thenReturn(List.of(measurement(network, 200, 3, 0.2, -55, NOW)));
        when(results.save(any(AnalysisResult.class))).thenAnswer(invocation -> {
            AnalysisResult saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });
    }

    @Test
    void storesResultUpdatesStateAndPublishesStatusChange() {
        when(strategy.analyze(any())).thenReturn(AnalysisOutcome.of(NetworkStatus.CRITICAL, 1, "latency"));

        var response = facade.analyze(1L, AnalysisType.THRESHOLD, null);

        assertThat(response.detectedStatus()).isEqualTo(NetworkStatus.CRITICAL);
        assertThat(network.getStatus()).isEqualTo(NetworkStatus.CRITICAL);
        ArgumentCaptor<NetworkEvent> captor = ArgumentCaptor.forClass(NetworkEvent.class);
        verify(events, times(2)).publish(captor.capture());
        assertThat(captor.getAllValues().getFirst()).isInstanceOf(NetworkStatusChangedEvent.class);
        verify(predictions, never()).save(any());
    }

    @Test
    void savesAiPredictionWhenTheStrategyUsedTheAiService() {
        AiAnalysisResponse ai = new AiAnalysisResponse(true, 0.8, AiPrediction.Severity.HIGH, "PROVIDER_ISSUE",
                "Causa probable: falla del proveedor de internet.", "msg", "rec", "CLAUDE", List.of("latency"),
                Map.of("latency", 4.2), 1, 1, List.of(), "isolation-forest-1.1", true);
        when(strategy.analyze(any())).thenReturn(new AnalysisOutcome(NetworkStatus.CRITICAL, 0.8, "msg", ai));
        when(predictions.save(any(AiPrediction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = facade.analyze(1L, AnalysisType.THRESHOLD, null);

        assertThat(response.prediction()).isNotNull();
        assertThat(response.prediction().simulatedData()).isTrue();
        assertThat(response.prediction().recommendationSource()).isEqualTo("CLAUDE");
        assertThat(response.prediction().anomalyType()).isEqualTo(AnomalyType.PROVIDER_ISSUE);
    }

    @Test
    void collectsFreshDataWhenNetworkHasNoHistory() {
        when(measurements.findByNetworkIdOrderByMeasuredAtDesc(eq(1L), any()))
                .thenReturn(List.of())
                .thenReturn(List.of(measurement(network, 20, 3, 0.2, -55, NOW)));
        when(strategy.analyze(any())).thenReturn(AnalysisOutcome.of(NetworkStatus.NORMAL, 0.1, "ok"));

        facade.analyze(1L, AnalysisType.THRESHOLD, null);

        verify(collection).collect(1L);
    }

    @Test
    void rejectsUnknownNetworkAndUnsupportedStrategy() {
        assertThatThrownBy(() -> facade.analyze(99L, AnalysisType.THRESHOLD, null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> facade.analyze(1L, AnalysisType.STATISTICAL, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
