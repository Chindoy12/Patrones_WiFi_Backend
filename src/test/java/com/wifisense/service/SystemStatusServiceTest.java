package com.wifisense.service;

import com.wifisense.analysis.AiAnalysisClient;
import com.wifisense.model.Alert;
import com.wifisense.repository.AlertRepository;
import com.wifisense.repository.MeasurementRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SystemStatusServiceTest {

    private Instant now = Instant.parse("2026-10-12T12:00:00Z");
    private final Clock clock = new Clock() {
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    };
    private final AiAnalysisClient ai = mock(AiAnalysisClient.class);
    private final MeasurementRepository measurements = mock(MeasurementRepository.class);
    private final AlertRepository alerts = mock(AlertRepository.class);
    private final SystemStatusService service = new SystemStatusService(ai, measurements, alerts, true, clock);

    @Test
    void reportsEachPieceAndCachesTheAiCheck() {
        when(ai.isHealthy()).thenReturn(true, false);
        when(measurements.findTopByOrderByMeasuredAtDesc()).thenReturn(Optional.empty());
        when(alerts.countByStatusNot(Alert.Status.RESOLVED)).thenReturn(4L);

        var first = service.status();
        var second = service.status();

        assertThat(first.aiService()).isEqualTo("UP");
        assertThat(second.aiService()).isEqualTo("UP");  // cached: the AI was asked once
        assertThat(first.openAlerts()).isEqualTo(4);
        assertThat(first.database()).isEqualTo("UP");
        assertThat(first.lastMeasurementAt()).isNull();
        verify(ai, times(1)).isHealthy();

        now = now.plus(SystemStatusService.AI_CHECK_TTL).plusSeconds(1);

        assertThat(service.status().aiService()).isEqualTo("DOWN");
    }
}
