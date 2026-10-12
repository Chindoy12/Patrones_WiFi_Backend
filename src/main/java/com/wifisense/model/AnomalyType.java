package com.wifisense.model;

import java.util.Arrays;

/** Probable cause of an anomaly, as diagnosed by WiFiSense-ai. */
public enum AnomalyType {
    NONE, CONGESTION, INTERFERENCE, WEAK_COVERAGE, PROVIDER_ISSUE, SUSPICIOUS_TRAFFIC, UNKNOWN;

    /**
     * Reads the AI value defensively: an unexpected or missing value never breaks an analysis, and the result always
     * agrees with the verdict (a detected anomaly has a cause, a normal result has none), as the database requires.
     */
    public static AnomalyType fromAi(String value, boolean anomalyDetected) {
        if (!anomalyDetected) {
            return NONE;
        }
        return Arrays.stream(values())
                .filter(type -> type != NONE && type.name().equals(value))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
