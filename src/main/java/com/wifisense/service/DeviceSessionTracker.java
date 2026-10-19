package com.wifisense.service;

import com.wifisense.dto.DeviceSessionHistory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * When each device was connected (tables of V37), for Mi Wi-Fi and for the sensors of the team networks.
 * <p>
 * Each report opens a period for a device that was not connected and extends the open period of one that was. A phone
 * at rest sometimes does not answer for a few minutes while still connected, so a period is closed only when the device
 * has been missing for longer than {@code wifisense.agent.session-gap} (12 minutes: two reports of the agent and some
 * margin); it then ends at the last time it was seen. If the agent stops reporting, the open periods are shown as ended
 * at their last report. Called inside the transaction that records the devices of the report, after they are flushed.
 */
@Component
public class DeviceSessionTracker {

    /** The two kinds of network; the table and column names are fixed here, never taken from a request. */
    public enum Owner {
        HOME("agent_device_sessions", "home_network_id"),
        TEAM("network_sensor_device_sessions", "network_id");

        final String table;
        final String column;

        Owner(String table, String column) {
            this.table = table;
            this.column = column;
        }
    }

    static final int MAX_DAYS = 30;

    private final JdbcTemplate jdbc;
    private final Duration gap;
    private final Clock clock;

    public DeviceSessionTracker(JdbcTemplate jdbc, @Value("${wifisense.agent.session-gap:12m}") Duration gap,
                                Clock clock) {
        this.jdbc = jdbc;
        this.gap = gap;
        this.clock = clock;
    }

    /** Records which devices one report saw at {@code now}, and closes the periods of those missing for too long. */
    public void track(Owner owner, Long networkId, Collection<String> present, Instant now) {
        Timestamp at = Timestamp.from(now);
        for (String fingerprint : present) {
            int extended = jdbc.update("UPDATE " + owner.table + " SET last_seen_at = ? WHERE " + owner.column
                    + " = ? AND fingerprint = ? AND disconnected_at IS NULL AND last_seen_at <= ?",
                    at, networkId, fingerprint, at);
            if (extended == 0 && !hasOpenPeriod(owner, networkId, fingerprint)) {
                jdbc.update("INSERT INTO " + owner.table + " (" + owner.column
                        + ", fingerprint, connected_at, last_seen_at) VALUES (?, ?, ?, ?)", networkId, fingerprint,
                        at, at);
            }
        }
        jdbc.update("UPDATE " + owner.table + " SET disconnected_at = last_seen_at WHERE " + owner.column
                + " = ? AND disconnected_at IS NULL AND last_seen_at < ?", networkId, Timestamp.from(now.minus(gap)));
    }

    private boolean hasOpenPeriod(Owner owner, Long networkId, String fingerprint) {
        Integer open = jdbc.queryForObject("SELECT count(*) FROM " + owner.table + " WHERE " + owner.column
                + " = ? AND fingerprint = ? AND disconnected_at IS NULL", Integer.class, networkId, fingerprint);
        return open != null && open > 0;
    }

    /** The periods of a device that overlap the last {@code days} days (1 to 30), newest first. */
    public DeviceSessionHistory history(Owner owner, Long networkId, String fingerprint, int days) {
        int span = Math.max(1, Math.min(days, MAX_DAYS));
        Instant now = Instant.now(clock);
        Instant since = now.minus(Duration.ofDays(span));
        List<DeviceSessionHistory.Period> periods = new ArrayList<>();
        jdbc.query("SELECT connected_at, last_seen_at, disconnected_at FROM " + owner.table + " WHERE " + owner.column
                + " = ? AND fingerprint = ? AND last_seen_at >= ? ORDER BY connected_at DESC LIMIT 500", rs -> {
            Instant start = rs.getTimestamp("connected_at").toInstant();
            Instant last = rs.getTimestamp("last_seen_at").toInstant();
            Timestamp closed = rs.getTimestamp("disconnected_at");
            boolean ongoing = closed == null && !last.isBefore(now.minus(gap));
            Instant end = closed != null ? closed.toInstant() : ongoing ? null : last;
            Instant counted = end == null ? now : end;
            Instant from = start.isBefore(since) ? since : start;
            periods.add(new DeviceSessionHistory.Period(start, end, ongoing,
                    Math.max(0, Duration.between(from, counted).toMinutes())));
        }, networkId, fingerprint, Timestamp.from(since));
        long total = periods.stream().mapToLong(DeviceSessionHistory.Period::minutes).sum();
        return new DeviceSessionHistory(fingerprint, span, periods.size(), total, periods);
    }

    /** Nightly cleanup: periods that ended (or were last seen) before {@code before}. */
    public int deleteOlderThan(Instant before) {
        Timestamp at = Timestamp.from(before);
        return jdbc.update("DELETE FROM agent_device_sessions WHERE last_seen_at < ?", at)
                + jdbc.update("DELETE FROM network_sensor_device_sessions WHERE last_seen_at < ?", at);
    }
}
