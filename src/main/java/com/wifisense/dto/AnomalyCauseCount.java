package com.wifisense.dto;

import com.wifisense.model.AnomalyType;

/** How many anomalies had each probable cause. UNCLASSIFIED groups predictions saved before causes existed. */
public record AnomalyCauseCount(String anomalyType, long count) {

    public static final String UNCLASSIFIED = "UNCLASSIFIED";

    public static AnomalyCauseCount of(AnomalyType type, long count) {
        return new AnomalyCauseCount(type == null ? UNCLASSIFIED : type.name(), count);
    }
}
