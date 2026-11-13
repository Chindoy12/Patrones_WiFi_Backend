package com.wifisense.service;

import com.wifisense.dto.LiveView.Note;
import com.wifisense.dto.LiveView.Point;
import com.wifisense.dto.LiveView.Summary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LiveSummaryTest {

    private static Point point(Double down, Double up, Double internetMs, Double gatewayMs, Integer signal) {
        return new Point(1, Instant.EPOCH, down, up, internetMs, internetMs == null, gatewayMs, false, signal, null,
                null);
    }

    /** {@code count} samples with the same numbers. */
    private static List<Point> steady(int count, Double internetMs, Double gatewayMs, Integer signal) {
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            points.add(point(1.0, 0.5, internetMs, gatewayMs, signal));
        }
        return points;
    }

    private static List<String> levels(Summary summary) {
        return summary.notes().stream().map(Note::level).toList();
    }

    @Test
    void theLastMinuteInNumbers() {
        List<Point> points = List.of(point(2.0, 0.5, 40.0, 3.0, 80), point(10.0, 1.5, 60.0, 5.0, 75),
                point(6.0, 1.0, null, 4.0, 70), point(4.0, 0.2, 50.0, null, 72));

        Summary summary = LiveSummary.of(points);

        assertThat(summary.samples()).isEqualTo(4);
        assertThat(summary.downloadMbps()).isEqualTo(5.5);
        assertThat(summary.peakDownloadMbps()).isEqualTo(10.0);
        assertThat(summary.uploadMbps()).isEqualTo(0.8);
        assertThat(summary.peakUploadMbps()).isEqualTo(1.5);
        assertThat(summary.internetMs()).isEqualTo(50.0);  // median of 40, 60 and 50
        assertThat(summary.jitterMs()).isEqualTo(20.0);  // 40 -> 60; the lost sample breaks the next pair
        assertThat(summary.lossPct()).isEqualTo(25.0);
        assertThat(summary.gatewayMs()).isEqualTo(4.0);
        assertThat(summary.signalPercent()).isEqualTo(72);  // the newest
        assertThat(summary.notes()).isEmpty();  // fewer than 5 samples: too little to say anything
    }

    @Test
    void withoutSamplesEverythingIsUnknown() {
        Summary summary = LiveSummary.of(List.of());

        assertThat(summary.samples()).isZero();
        assertThat(summary.downloadMbps()).isNull();
        assertThat(summary.internetMs()).isNull();
        assertThat(summary.lossPct()).isNull();
        assertThat(summary.notes()).isEmpty();
    }

    @Test
    void aGoodMomentSaysSo() {
        Summary summary = LiveSummary.of(steady(10, 35.0, 3.0, 80));

        assertThat(summary.notes()).containsExactly(new Note("OK",
                "Todo bien en este momento: 35 ms hasta internet y sin pérdidas."));
    }

    @Test
    void theSameThresholdsAsTheGradeOfTheConnection() {
        assertThat(levels(LiveSummary.of(steady(10, 160.0, 3.0, 80)))).contains("BAD");
        assertThat(levels(LiveSummary.of(steady(10, 120.0, 3.0, 80)))).contains("WARN").doesNotContain("BAD");
        assertThat(levels(LiveSummary.of(steady(10, 90.0, 3.0, 80)))).containsExactly("OK");
    }

    @Test
    void lostAnswersFromInternetAreCounted() {
        List<Point> points = steady(19, 30.0, 3.0, 80);
        points.add(point(1.0, 0.5, null, 3.0, 80));  // 1 of 20: 5 %

        Summary summary = LiveSummary.of(points);

        assertThat(summary.lossPct()).isEqualTo(5.0);
        assertThat(summary.notes().get(0)).isEqualTo(new Note("BAD",
                "Se está perdiendo el 5 % de las respuestas de internet: las videollamadas y los juegos se van a "
                        + "cortar."));
    }

    @Test
    void aJumpyLatencyIsJitter() {
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            points.add(point(1.0, 0.5, i % 2 == 0 ? 20.0 : 70.0, 3.0, 80));
        }

        assertThat(LiveSummary.of(points).notes()).extracting(Note::text)
                .anyMatch(text -> text.startsWith("La latencia cambia mucho de un momento a otro (jitter de 50 ms)"));
    }

    @Test
    void aSlowRouterMeansTheDelayStartsInTheWifiOfThisComputer() {
        Summary summary = LiveSummary.of(steady(10, 120.0, 60.0, 80));

        assertThat(summary.notes()).extracting(Note::text)
                .anyMatch(text -> text.startsWith("El retraso empieza antes del router: llegar hasta él ya toma 60 ms"));
    }

    @Test
    void aFastRouterWithASlowInternetMeansTheProviderOrTheMobileData() {
        Summary summary = LiveSummary.of(steady(10, 180.0, 4.0, 80));

        assertThat(summary.notes()).extracting(Note::text).anyMatch(text -> text.equals(
                "El router responde rápido (4 ms): la demora está después de él, en el internet del proveedor o en "
                        + "los datos móviles del celular."));
    }

    @Test
    void aRouterThatStopsAnsweringMeansTheWifiIsDropping() {
        List<Point> points = new ArrayList<>(steady(8, 30.0, 3.0, 80));
        points.add(new Point(1, Instant.EPOCH, 1.0, 0.5, 30.0, false, null, true, 80, null, null));
        points.add(new Point(1, Instant.EPOCH, 1.0, 0.5, 30.0, false, null, true, 80, null, null));

        assertThat(LiveSummary.of(points).notes()).extracting(Note::text)
                .anyMatch(text -> text.startsWith("El router no respondió el 20 % de las veces"));
    }

    @Test
    void aWeakSignalIsSaid() {
        assertThat(LiveSummary.of(steady(10, 30.0, 3.0, 35)).notes()).extracting(Note::text)
                .anyMatch(text -> text.startsWith("La señal Wi-Fi llega débil (35 %)"));
    }

    @Test
    void aRouterWithoutPortsDoesNotBlameAnybody() {
        Summary summary = LiveSummary.of(steady(10, 180.0, null, 80));

        assertThat(summary.gatewayMs()).isNull();
        assertThat(summary.notes()).extracting(Note::text).noneMatch(text -> text.contains("router"));
    }
}
