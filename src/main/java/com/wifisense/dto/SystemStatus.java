package com.wifisense.dto;

import java.time.Instant;

/** Status strip of the team dashboard. The database is UP whenever this answer exists, since it was just read. */
public record SystemStatus(String aiService, String database, boolean monitoringEnabled, Instant lastMeasurementAt,
                           long openAlerts, Instant checkedAt) {
}
