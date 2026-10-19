package com.wifisense.repository;

import com.wifisense.dto.WeeklyReport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The numbers of the weekly report, computed by PostgreSQL with the indexes by network and date. Days and hours are
 * those of {@code zone} (the measurements are stored in UTC), so a day of the report is a day in Colombia.
 */
@Repository
public class WeeklyReportRepository {

    private final JdbcTemplate jdbc;

    public WeeklyReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Per day: measurements, hours with a measurement, averages and the most devices connected at once. */
    public List<WeeklyReport.Day> days(Long networkId, Instant from, Instant to, String zone) {
        Map<LocalDate, Double> traffic = trafficPerDay(networkId, from, to, zone);
        Map<LocalDate, Long> alerts = new HashMap<>();
        jdbc.query("SELECT (created_at AT TIME ZONE ?)::date AS day, count(*) AS n FROM alerts "
                        + "WHERE network_id = ? AND created_at >= ? AND created_at < ? GROUP BY 1",
                rs -> { alerts.put(rs.getObject("day", LocalDate.class), rs.getLong("n")); },
                zone, networkId, Timestamp.from(from), Timestamp.from(to));
        return jdbc.query("SELECT (measured_at AT TIME ZONE ?)::date AS day, count(*) AS n, "
                        + "count(DISTINCT date_trunc('hour', measured_at AT TIME ZONE ?)) AS hours, "
                        + "avg(latency_ms) AS latency, avg(jitter_ms) AS jitter, avg(packet_loss_pct) AS loss, "
                        + "avg(signal_strength_dbm) AS signal, max(connected_devices) AS devices "
                        + "FROM measurements WHERE network_id = ? AND measured_at >= ? AND measured_at < ? "
                        + "GROUP BY 1 ORDER BY 1",
                (rs, row) -> {
                    LocalDate day = rs.getObject("day", LocalDate.class);
                    return new WeeklyReport.Day(day, rs.getLong("n"), rs.getInt("hours"), number(rs, "latency"),
                            number(rs, "jitter"), number(rs, "loss"), number(rs, "signal"),
                            (Integer) rs.getObject("devices"), traffic.get(day), alerts.getOrDefault(day, 0L));
                },
                zone, zone, networkId, Timestamp.from(from), Timestamp.from(to));
    }

    private Map<LocalDate, Double> trafficPerDay(Long networkId, Instant from, Instant to, String zone) {
        Map<LocalDate, Double> traffic = new HashMap<>();
        jdbc.query("SELECT (observed_at AT TIME ZONE ?)::date AS day, sum(traffic_volume_mb) AS mb "
                        + "FROM traffic_observations WHERE network_id = ? AND observed_at >= ? AND observed_at < ? "
                        + "GROUP BY 1",
                rs -> { traffic.put(rs.getObject("day", LocalDate.class), rs.getDouble("mb")); },
                zone, networkId, Timestamp.from(from), Timestamp.from(to));
        return traffic;
    }

    /** Totals of a period (the week or the one before), to compare them. */
    public WeeklyReport.Totals totals(Long networkId, Instant from, Instant to) {
        double hours = Math.max(1, java.time.Duration.between(from, to).toHours());
        return jdbc.queryForObject("SELECT count(*) AS n, avg(latency_ms) AS latency, avg(jitter_ms) AS jitter, "
                        + "avg(packet_loss_pct) AS loss, count(DISTINCT date_trunc('hour', measured_at)) AS hours, "
                        + "(SELECT sum(traffic_volume_mb) FROM traffic_observations t WHERE t.network_id = ? "
                        + "AND t.observed_at >= ? AND t.observed_at < ?) AS mb, "
                        + "(SELECT count(*) FROM alerts a WHERE a.network_id = ? AND a.created_at >= ? "
                        + "AND a.created_at < ?) AS alerts, "
                        + "(SELECT count(*) FROM alerts a WHERE a.network_id = ? AND a.created_at >= ? "
                        + "AND a.created_at < ? AND a.severity = 'CRITICAL') AS critical "
                        + "FROM measurements WHERE network_id = ? AND measured_at >= ? AND measured_at < ?",
                (rs, row) -> new WeeklyReport.Totals(rs.getLong("n"), number(rs, "latency"), number(rs, "jitter"),
                        number(rs, "loss"), Math.round(rs.getLong("hours") / hours * 1000) / 10.0,
                        number(rs, "mb"), rs.getLong("alerts"), rs.getLong("critical")),
                networkId, Timestamp.from(from), Timestamp.from(to), networkId, Timestamp.from(from),
                Timestamp.from(to), networkId, Timestamp.from(from), Timestamp.from(to), networkId,
                Timestamp.from(from), Timestamp.from(to));
    }

    /** The hour of the day (in the zone) with the highest average latency, with at least three measurements. */
    public WeeklyReport.WorstHour worstHour(Long networkId, Instant from, Instant to, String zone) {
        List<WeeklyReport.WorstHour> found = jdbc.query("SELECT extract(hour FROM measured_at AT TIME ZONE ?)::int "
                        + "AS h, avg(latency_ms) AS latency, count(*) AS n FROM measurements WHERE network_id = ? "
                        + "AND measured_at >= ? AND measured_at < ? AND latency_ms IS NOT NULL GROUP BY 1 "
                        + "HAVING count(*) >= 3 ORDER BY 2 DESC, 1 LIMIT 1",
                (rs, row) -> new WeeklyReport.WorstHour(rs.getInt("h"), rs.getDouble("latency"), rs.getLong("n")),
                zone, networkId, Timestamp.from(from), Timestamp.from(to));
        return found.isEmpty() ? null : found.get(0);
    }

    public List<WeeklyReport.Protocol> protocols(Long networkId, Instant from, Instant to) {
        return jdbc.query("SELECT protocol, sum(packet_count) AS packets FROM protocol_statistics "
                        + "WHERE network_id = ? AND period_start >= ? AND period_start < ? GROUP BY 1 ORDER BY 2 DESC",
                (rs, row) -> new WeeklyReport.Protocol(rs.getString("protocol"), rs.getLong("packets")),
                networkId, Timestamp.from(from), Timestamp.from(to));
    }

    /** Devices the sensor saw in the period and how many appeared for the first time (not counting its first report). */
    public WeeklyReport.Devices devices(Long networkId, Instant from, Instant to) {
        return jdbc.queryForObject("SELECT count(*) FILTER (WHERE last_seen_at >= ? AND first_seen_at < ?) AS seen, "
                        + "count(*) FILTER (WHERE first_seen_at >= ? AND first_seen_at < ? AND first_seen_at > "
                        + "(SELECT min(first_seen_at) FROM network_sensor_devices m WHERE m.network_id = ?)) AS fresh "
                        + "FROM network_sensor_devices WHERE network_id = ?",
                (rs, row) -> new WeeklyReport.Devices(rs.getLong("seen"), rs.getLong("fresh")),
                Timestamp.from(from), Timestamp.from(to), Timestamp.from(from), Timestamp.from(to), networkId,
                networkId);
    }

    /** Latest and lowest security score the AI gave the sensor's reports in the period; null without audits. */
    public WeeklyReport.Security security(Long networkId, Instant from, Instant to) {
        List<WeeklyReport.Security> found = jdbc.query("SELECT security_score, security_grade, "
                        + "min(security_score) OVER () AS lowest FROM network_sensor_reports WHERE network_id = ? "
                        + "AND received_at >= ? AND received_at < ? AND security_score IS NOT NULL "
                        + "ORDER BY received_at DESC LIMIT 1",
                (rs, row) -> new WeeklyReport.Security(rs.getInt("security_score"), rs.getString("security_grade"),
                        rs.getInt("lowest")),
                networkId, Timestamp.from(from), Timestamp.from(to));
        return found.isEmpty() ? null : found.get(0);
    }

    private static Double number(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : Math.round(((Number) value).doubleValue() * 100) / 100.0;
    }
}
