package com.wifisense.service;

import com.wifisense.analysis.AiAnalysisClient;
import com.wifisense.dto.SystemStatus;
import com.wifisense.model.Alert;
import com.wifisense.model.Measurement;
import com.wifisense.repository.AlertRepository;
import com.wifisense.repository.MeasurementRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Health of the pieces behind the dashboard. The AI check is cached so the dashboard never hammers it. */
@Service
public class SystemStatusService {

    static final Duration AI_CHECK_TTL = Duration.ofSeconds(30);

    private final AiAnalysisClient aiClient;
    private final MeasurementRepository measurements;
    private final AlertRepository alerts;
    private final boolean monitoringEnabled;
    private final Clock clock;
    private volatile Instant aiCheckedAt = Instant.MIN;
    private volatile boolean aiUp;

    public SystemStatusService(AiAnalysisClient aiClient, MeasurementRepository measurements, AlertRepository alerts,
                               @Value("${wifisense.monitoring.enabled:true}") boolean monitoringEnabled, Clock clock) {
        this.aiClient = aiClient;
        this.measurements = measurements;
        this.alerts = alerts;
        this.monitoringEnabled = monitoringEnabled;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SystemStatus status() {
        Instant now = clock.instant();
        return new SystemStatus(aiAvailable(now) ? "UP" : "DOWN", "UP", monitoringEnabled,
                measurements.findTopByOrderByMeasuredAtDesc().map(Measurement::getMeasuredAt).orElse(null),
                alerts.countByStatusNot(Alert.Status.RESOLVED), now);
    }

    private boolean aiAvailable(Instant now) {
        if (aiCheckedAt.plus(AI_CHECK_TTL).isBefore(now)) {
            aiUp = aiClient.isHealthy();
            aiCheckedAt = now;
        }
        return aiUp;
    }
}
