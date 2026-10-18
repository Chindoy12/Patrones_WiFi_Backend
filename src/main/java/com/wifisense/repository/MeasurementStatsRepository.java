package com.wifisense.repository;

import com.wifisense.dto.MetricSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Report statistics computed by PostgreSQL in one query (index idx_measurements_network_time): average, minimum,
 * maximum and 95th percentile of each metric. Before, a 31-day report loaded every measurement into memory (about
 * 45 000 per network checked every minute, up to six networks in a comparison) only to compute these numbers.
 * percentile_disc(0.95) is the nearest-rank percentile, the same value the Java version took.
 */
@Repository
public class MeasurementStatsRepository {

    public record Stats(long samples, MetricSummary latency, MetricSummary jitter, MetricSummary packetLoss,
                        MetricSummary bandwidth, MetricSummary signal, boolean simulated) {
    }

    private static final List<String> METRICS = List.of("latency_ms", "jitter_ms", "packet_loss_pct",
            "bandwidth_mbps", "signal_strength_dbm");

    static final String SQL = "SELECT count(*) AS samples, "
            + METRICS.stream().map(m -> "avg(" + m + ") AS " + m + "_avg, min(" + m + ") AS " + m + "_min, max(" + m
                    + ") AS " + m + "_max, percentile_disc(0.95) WITHIN GROUP (ORDER BY " + m + ") AS " + m
                    + "_p95, count(" + m + ") AS " + m + "_n")
            .collect(Collectors.joining(", "))
            + ", coalesce(bool_or(source = 'SIMULATION'), false) AS simulated"
            + " FROM measurements WHERE network_id = ? AND measured_at BETWEEN ? AND ?";

    private final JdbcTemplate jdbc;

    public MeasurementStatsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Measurements of the network with measured_at between {@code from} and {@code to}, both included. */
    public Stats summarize(Long networkId, Instant from, Instant to) {
        return jdbc.queryForObject(SQL, (rs, row) -> new Stats(rs.getLong("samples"), metric(rs, "latency_ms"),
                        metric(rs, "jitter_ms"), metric(rs, "packet_loss_pct"), metric(rs, "bandwidth_mbps"),
                        metric(rs, "signal_strength_dbm"), rs.getBoolean("simulated")),
                networkId, Timestamp.from(from), Timestamp.from(to));
    }

    private static MetricSummary metric(ResultSet rs, String column) throws SQLException {
        return MetricSummary.of(number(rs, column + "_avg"), number(rs, column + "_min"), number(rs, column + "_max"),
                number(rs, column + "_p95"), rs.getInt(column + "_n"));
    }

    private static Double number(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).doubleValue();
    }
}
