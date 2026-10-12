package com.wifisense.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnomalyTypeTest {

    @Test
    void readsTheCauseSentByTheAi() {
        assertThat(AnomalyType.fromAi("INTERFERENCE", true)).isEqualTo(AnomalyType.INTERFERENCE);
    }

    @Test
    void unknownOrMissingCauseOfAnAnomalyBecomesUnknown() {
        assertThat(AnomalyType.fromAi("SOLAR_FLARE", true)).isEqualTo(AnomalyType.UNKNOWN);
        assertThat(AnomalyType.fromAi(null, true)).isEqualTo(AnomalyType.UNKNOWN);
        assertThat(AnomalyType.fromAi("NONE", true)).isEqualTo(AnomalyType.UNKNOWN);
    }

    @Test
    void normalResultNeverCarriesACause() {
        assertThat(AnomalyType.fromAi("CONGESTION", false)).isEqualTo(AnomalyType.NONE);
    }
}
