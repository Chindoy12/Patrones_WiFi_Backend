package com.wifisense.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifisense.analysis.AiAnalysisClient;
import com.wifisense.analysis.AiQualityRequest;
import com.wifisense.analysis.InsufficientDataException;
import com.wifisense.model.Network;
import com.wifisense.model.NetworkStatus;
import com.wifisense.repository.MeasurementRepository;
import com.wifisense.repository.NetworkRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static com.wifisense.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NetworkQualityServiceTest {

    private final NetworkRepository networks = mock(NetworkRepository.class);
    private final MeasurementRepository measurements = mock(MeasurementRepository.class);
    private final AiAnalysisClient ai = mock(AiAnalysisClient.class);
    private final NetworkQualityService service = new NetworkQualityService(networks, measurements, ai);
    private final Network guest = network(1, NetworkStatus.WARNING);

    @Test
    void gradesTheLatestMeasurementOfTheNetwork() throws Exception {
        when(networks.findById(1L)).thenReturn(Optional.of(guest));
        when(measurements.findByNetworkIdOrderByMeasuredAtDesc(eq(1L), any()))
                .thenReturn(List.of(measurement(guest, 45, 8, 1.3, -71, NOW)));
        when(ai.qualityScore(any())).thenReturn(new ObjectMapper().readTree("{\"score\":61,\"grade\":\"FAIR\"}"));

        var quality = service.quality(1L);

        assertThat(quality.quality().get("score").asInt()).isEqualTo(61);
        assertThat(quality.measuredAt()).isEqualTo(NOW);
        ArgumentCaptor<AiQualityRequest> sent = ArgumentCaptor.forClass(AiQualityRequest.class);
        verify(ai).qualityScore(sent.capture());
        assertThat(sent.getValue().latencyMs()).isEqualTo(45);
        assertThat(sent.getValue().signalDbm()).isEqualTo(-71.0);
        assertThat(sent.getValue().uploadMbps()).isNull();
    }

    @Test
    void networkWithoutMeasurementsCannotBeGraded() {
        when(networks.findById(1L)).thenReturn(Optional.of(guest));
        when(measurements.findByNetworkIdOrderByMeasuredAtDesc(eq(1L), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.quality(1L)).isInstanceOf(InsufficientDataException.class)
                .hasMessage("La red aún no tiene mediciones.");
        verifyNoInteractions(ai);
    }

    @Test
    void unknownNetworkIsNotFound() {
        when(networks.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.quality(9L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
