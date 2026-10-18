package com.wifisense.service;

import com.wifisense.security.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

class MonitoringRetentionJobTest {

    private static final Instant NOW = Instant.parse("2026-10-17T04:00:00Z");

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SessionService sessions = mock(SessionService.class);
    private final MonitoringRetentionJob job = new MonitoringRetentionJob(jdbc, sessions,
            new RetentionProperties(Duration.ofDays(90), 100), Clock.fixed(NOW, ZoneOffset.UTC));

    // any(Timestamp.class) and anyInt() pick update(String, Object...), the overload the job calls
    @Test
    void eachTableIsEmptiedInBatchesUntilABatchComesBackShort() {
        when(jdbc.update(anyString(), any(Timestamp.class), anyInt())).thenReturn(0);
        when(jdbc.update(startsWith("DELETE FROM measurements "), any(Timestamp.class), anyInt()))
                .thenReturn(100, 100, 37);

        Map<String, Integer> deleted = job.purgeOlderThan(NOW.minus(Duration.ofDays(90)));

        assertThat(deleted).containsEntry("measurements", 237).containsEntry("alerts", 0).hasSize(6);
        verify(jdbc, times(3)).update(startsWith("DELETE FROM measurements "),
                eq(Timestamp.from(Instant.parse("2026-07-19T04:00:00Z"))), eq(100));
    }

    @Test
    void onlyResolvedAlertsAreDeletedAndOnlyByTheirIndexedDate() {
        assertThat(MonitoringRetentionJob.RULES).allSatisfy(rule -> assertThat(rule.sql())
                .startsWith("DELETE FROM " + rule.table() + " WHERE id IN (SELECT id FROM " + rule.table() + " WHERE ")
                .endsWith(" LIMIT ?)"));
        assertThat(MonitoringRetentionJob.RULES.getLast().sql())
                .isEqualTo("DELETE FROM alerts WHERE id IN (SELECT id FROM alerts WHERE resolved_at < ? "
                        + "AND status = 'RESOLVED' LIMIT ?)");
    }

    @Test
    void aTableThatFailsDoesNotStopTheOthersNorTheTokenCleanup() {
        when(jdbc.update(anyString(), any(Timestamp.class), anyInt())).thenReturn(0);
        when(jdbc.update(startsWith("DELETE FROM traffic_sessions "), any(Timestamp.class), anyInt()))
                .thenThrow(new QueryTimeoutException("lock"));

        job.purge();

        verify(jdbc).update(startsWith("DELETE FROM alerts "), any(Timestamp.class), anyInt());
        verify(sessions).purgeExpired();
    }
}
