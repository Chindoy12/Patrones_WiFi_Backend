package com.wifisense.service;

import com.wifisense.security.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every night deletes the team's monitoring data older than the retention period (wifisense.retention.monitoring)
 * and the refresh tokens that already expired. Without it a network checked every minute adds about 45 000
 * measurements a month, plus their traffic and protocol rows, forever.
 * <p>
 * Each table is emptied in small batches, each in its own short transaction, so the monitoring and the users never
 * wait behind a long delete. Every condition uses a date index (database migration V27).
 */
@Component
public class MonitoringRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(MonitoringRetentionJob.class);

    /** Table, date column and extra condition. Fixed strings: nothing here comes from a request. */
    record Rule(String table, String column, String condition) {

        String sql() {
            String where = column + " < ?" + (condition.isEmpty() ? "" : " AND " + condition);
            return "DELETE FROM " + table + " WHERE id IN (SELECT id FROM " + table + " WHERE " + where + " LIMIT ?)";
        }
    }

    static final List<Rule> RULES = List.of(
            new Rule("measurements", "measured_at", ""),
            new Rule("traffic_observations", "observed_at", ""),
            new Rule("traffic_sessions", "started_at", ""),
            new Rule("protocol_statistics", "period_start", ""),
            // their AI predictions go with them (ON DELETE CASCADE); alerts stay and just lose the link
            new Rule("analysis_results", "created_at", ""),
            new Rule("alerts", "resolved_at", "status = 'RESOLVED'"));

    private final JdbcTemplate jdbc;
    private final SessionService sessions;
    private final RetentionProperties properties;
    private final Clock clock;

    public MonitoringRetentionJob(JdbcTemplate jdbc, SessionService sessions, RetentionProperties properties,
                                  Clock clock) {
        this.jdbc = jdbc;
        this.sessions = sessions;
        this.properties = properties;
        this.clock = clock;
    }

    /** 04:00 UTC (23:00 in Colombia), after the agent cleanup of 03:30. */
    @Scheduled(cron = "${wifisense.retention.cron:0 0 4 * * *}", zone = "UTC")
    public void purge() {
        Map<String, Integer> deleted = purgeOlderThan(Instant.now(clock).minus(properties.monitoring()));
        int tokens = sessions.purgeExpired();
        int total = deleted.values().stream().mapToInt(Integer::intValue).sum() + tokens;
        if (total > 0) {
            log.info("Monitoring retention: deleted {} and {} expired refresh tokens", deleted, tokens);
        }
    }

    public Map<String, Integer> purgeOlderThan(Instant before) {
        Map<String, Integer> deleted = new LinkedHashMap<>();
        Timestamp limit = Timestamp.from(before);
        for (Rule rule : RULES) {
            try {
                int count = 0;
                int batch;
                do {
                    batch = jdbc.update(rule.sql(), limit, properties.batchSize());
                    count += batch;
                } while (batch == properties.batchSize());
                deleted.put(rule.table(), count);
            } catch (RuntimeException e) {
                // one table failing (for example, a lock timeout) must not stop the others
                log.warn("Monitoring retention of {} failed: {}", rule.table(), e.getMessage());
            }
        }
        return deleted;
    }
}
