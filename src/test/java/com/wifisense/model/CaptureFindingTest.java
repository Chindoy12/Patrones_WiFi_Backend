package com.wifisense.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaptureFindingTest {

    @Test
    void anObservationIsACountWithoutConfidence() {
        CaptureFinding fact = CaptureFinding.observed("BROADCAST_SHARE", FindingSeverity.INFO, "Difusión",
                "40 de 1200 paquetes.", null, FindingSource.RULES);

        assertThat(fact.getCategory()).isEqualTo(FindingCategory.OBSERVED);
        assertThat(fact.getConfidence()).isNull();
        assertThat(fact.getRecommendation()).isNull();
    }

    @Test
    void anInferenceAlwaysSaysHowSureItIsBetweenZeroAndOne() {
        assertThat(CaptureFinding.inferred("TRAFFIC_BURST", FindingSeverity.WARNING, "Ráfaga", "x", null, 0.625,
                FindingSource.AI).getConfidence()).isEqualByComparingTo(new BigDecimal("0.63"));
        assertThat(CaptureFinding.inferred("TRAFFIC_BURST", FindingSeverity.WARNING, "Ráfaga", "x", null, 7,
                FindingSource.AI).getConfidence()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(CaptureFinding.inferred("TRAFFIC_BURST", FindingSeverity.WARNING, "Ráfaga", "x", null, -2,
                FindingSource.AI).getConfidence()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(CaptureFinding.inferred("TRAFFIC_BURST", FindingSeverity.WARNING, "Ráfaga", "x", null, Double.NaN,
                FindingSource.AI).getConfidence()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void codesAreStableUpperCaseWords() {
        assertThatThrownBy(() -> CaptureFinding.observed("ráfaga de tráfico", FindingSeverity.INFO, "x", "x", null,
                FindingSource.AI)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CaptureFinding.observed(null, FindingSeverity.INFO, "x", "x", null,
                FindingSource.AI)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void longTextsAreCutToWhatTheTableKeepsAndEmptyOnesAreNeverBlank() {
        CaptureFinding fact = CaptureFinding.observed("LONG_TEXT", FindingSeverity.INFO, "t".repeat(300), "  ",
                "r".repeat(500), FindingSource.AI);

        assertThat(fact.getTitle()).hasSize(120).endsWith("…");
        assertThat(fact.getEvidence()).isEqualTo("—");
        assertThat(fact.getRecommendation()).hasSize(400);
    }
}
