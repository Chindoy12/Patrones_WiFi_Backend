package com.wifisense.dto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetricSummaryTest {

    @Test
    void theDatabaseAggregatesGiveTheSameSummaryAsTheValues() {
        List<Double> values = Arrays.asList(12.0, 15.5, null, 9.25, 40.0, 13.333);

        // what PostgreSQL returns for these values: avg, min, max, percentile_disc(0.95) and count of non-null
        MetricSummary fromDatabase = MetricSummary.of(18.016600000000004, 9.25, 40.0, 40.0, 5);

        assertThat(fromDatabase).isEqualTo(MetricSummary.of(values));
        assertThat(fromDatabase.average()).isEqualTo(18.02);
    }

    @Test
    void noSamplesIsAnEmptySummary() {
        assertThat(MetricSummary.of(null, null, null, null, 0)).isEqualTo(MetricSummary.of(List.of()));
    }
}
