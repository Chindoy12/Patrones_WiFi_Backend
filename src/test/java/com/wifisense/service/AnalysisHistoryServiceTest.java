package com.wifisense.service;

import com.wifisense.dto.AnomalyCauseCount;
import com.wifisense.model.AnomalyType;
import com.wifisense.repository.AiPredictionRepository;
import com.wifisense.repository.AnalysisResultRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AnalysisHistoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");

    private final AiPredictionRepository predictions = mock(AiPredictionRepository.class);
    private final AnalysisHistoryService service = new AnalysisHistoryService(mock(AnalysisResultRepository.class),
            predictions, Clock.fixed(NOW, ZoneOffset.UTC));

    private static AiPredictionRepository.CauseCount row(AnomalyType type, long total) {
        return new AiPredictionRepository.CauseCount() {
            public AnomalyType getAnomalyType() { return type; }
            public long getTotal() { return total; }
        };
    }

    @Test
    void groupsAnomaliesByCauseAndLabelsOldOnesAsUnclassified() {
        when(predictions.countAnomaliesByCauseSince(any()))
                .thenReturn(List.of(row(AnomalyType.CONGESTION, 7), row(null, 2)));

        List<AnomalyCauseCount> causes = service.anomalyCauses(30);

        assertThat(causes).containsExactly(new AnomalyCauseCount("CONGESTION", 7),
                new AnomalyCauseCount(AnomalyCauseCount.UNCLASSIFIED, 2));
        verify(predictions).countAnomaliesByCauseSince(NOW.minus(Duration.ofDays(30)));
    }

    @Test
    void periodIsKeptBetweenOneDayAndOneYear() {
        service.anomalyCauses(100_000);
        service.anomalyCauses(-5);

        verify(predictions).countAnomaliesByCauseSince(NOW.minus(Duration.ofDays(365)));
        verify(predictions).countAnomaliesByCauseSince(NOW.minus(Duration.ofDays(1)));
    }
}
