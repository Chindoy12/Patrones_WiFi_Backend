package com.wifisense.dto;

import java.util.List;
import java.util.Objects;

/** avg / min / max / 95th percentile of one metric over a report period. */
public record MetricSummary(Double average, Double min, Double max, Double p95, int samples) {

    public static MetricSummary of(List<? extends Number> values) {
        List<Double> sorted = values.stream().filter(Objects::nonNull).map(Number::doubleValue).sorted().toList();
        if (sorted.isEmpty()) {
            return new MetricSummary(null, null, null, null, 0);
        }
        double average = sorted.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        int p95Index = (int) Math.ceil(0.95 * sorted.size()) - 1;
        return new MetricSummary(round(average), sorted.getFirst(), sorted.getLast(), sorted.get(p95Index),
                sorted.size());
    }

    /** From values already aggregated (by the database): only the average is rounded, as above. */
    public static MetricSummary of(Double average, Double min, Double max, Double p95, int samples) {
        if (samples == 0 || average == null) {
            return new MetricSummary(null, null, null, null, 0);
        }
        return new MetricSummary(round(average), min, max, p95, samples);
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
