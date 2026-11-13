package com.wifisense.service;

import com.wifisense.dto.LiveView.Note;
import com.wifisense.dto.LiveView.Point;
import com.wifisense.dto.LiveView.Summary;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The last minute of «En vivo» and what it means. The thresholds are the ones the AI service uses to grade a
 * connection (a video call needs latency under 150 ms, jitter under 30 ms and loss under 2 %), so the live view and
 * the connection test never contradict each other. These are fixed rules over measured numbers, not a prediction.
 * <p>
 * The router latency says where a delay starts: slow up to the router means the Wi-Fi of this computer (distance,
 * walls, interference); fast up to the router but slow to internet means the provider or the mobile data behind it.
 */
public final class LiveSummary {

    static final int MIN_SAMPLES = 5;
    static final double LATENCY_WARN = 100;
    static final double LATENCY_BAD = 150;
    static final double JITTER_WARN = 30;
    static final double LOSS_WARN = 2;
    static final double LOSS_BAD = 5;
    static final double ROUTER_SLOW = 50;
    static final double ROUTER_FAST = 20;
    static final int SIGNAL_WEAK = 40;

    private LiveSummary() {
    }

    public static Summary of(List<Point> points) {
        List<Double> down = values(points.stream().map(Point::downloadMbps).toList());
        List<Double> up = values(points.stream().map(Point::uploadMbps).toList());
        List<Double> internet = values(points.stream().map(Point::internetMs).toList());
        List<Double> gateway = values(points.stream().map(Point::gatewayMs).toList());
        long probed = points.stream().filter(p -> p.internetMs() != null || p.internetLost()).count();
        long lost = points.stream().filter(Point::internetLost).count();
        long gatewayProbed = points.stream().filter(p -> p.gatewayMs() != null || p.gatewayLost()).count();
        long gatewayLost = points.stream().filter(Point::gatewayLost).count();
        Integer signal = points.stream().map(Point::signalPercent).filter(Objects::nonNull)
                .reduce((first, second) -> second).orElse(null);

        Double internetMs = median(internet);
        Double jitterMs = jitter(points);
        Double lossPct = probed == 0 ? null : round(100.0 * lost / probed, 1);
        Double gatewayMs = median(gateway);
        Double gatewayLossPct = gatewayProbed == 0 ? null : 100.0 * gatewayLost / gatewayProbed;

        List<Note> notes = points.size() < MIN_SAMPLES ? List.of()
                : notes(internetMs, jitterMs, lossPct, gatewayMs, gatewayLossPct, signal);
        return new Summary(points.size(), round(mean(down), 2), round(mean(up), 2), round(max(down), 2),
                round(max(up), 2), round(internetMs, 1), round(jitterMs, 1), lossPct, round(gatewayMs, 1), signal,
                notes);
    }

    static List<Note> notes(Double internetMs, Double jitterMs, Double lossPct, Double gatewayMs,
                            Double gatewayLossPct, Integer signal) {
        List<Note> notes = new ArrayList<>();
        if (lossPct != null && lossPct >= LOSS_BAD) {
            notes.add(new Note("BAD", "Se está perdiendo el " + whole(lossPct) + " % de las respuestas de internet: "
                    + "las videollamadas y los juegos se van a cortar."));
        } else if (lossPct != null && lossPct >= LOSS_WARN) {
            notes.add(new Note("WARN", "Se pierde el " + whole(lossPct) + " % de las respuestas de internet: una "
                    + "videollamada puede congelarse a ratos."));
        }
        if (internetMs != null && internetMs >= LATENCY_BAD) {
            notes.add(new Note("BAD", "La latencia hacia internet es alta (" + whole(internetMs) + " ms): las "
                    + "videollamadas y los juegos van a sentirse con retraso."));
        } else if (internetMs != null && internetMs >= LATENCY_WARN) {
            notes.add(new Note("WARN", "La latencia hacia internet está subiendo (" + whole(internetMs) + " ms): "
                    + "sirve para navegar, pero un juego en línea ya se nota lento."));
        }
        if (notes.isEmpty() && internetMs != null) {
            notes.add(new Note("OK", "Todo bien en este momento: " + whole(internetMs) + " ms hasta internet"
                    + (lossPct == null || lossPct == 0 ? " y sin pérdidas." : " y casi sin pérdidas.")));
        }
        return notes;
    }

    /** Mean change between consecutive answers of internet (a lost sample breaks the pair). */
    static Double jitter(List<Point> points) {
        double total = 0;
        int pairs = 0;
        Double previous = null;
        for (Point point : points) {
            Double current = point.internetMs();
            if (previous != null && current != null) {
                total += Math.abs(current - previous);
                pairs++;
            }
            previous = current;
        }
        return pairs == 0 ? null : total / pairs;
    }

    private static List<Double> values(List<Double> all) {
        return all.stream().filter(Objects::nonNull).toList();
    }

    static Double median(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }

    private static Double mean(List<Double> values) {
        return values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static Double max(List<Double> values) {
        return values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
    }

    static Double round(Double value, int decimals) {
        if (value == null) {
            return null;
        }
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }

    private static String whole(double value) {
        return String.valueOf(Math.round(value));
    }
}
