package com.wifisense.service;

import com.wifisense.model.AuditAction;
import com.wifisense.repository.AuditEventRepository;
import com.wifisense.repository.TrafficCaptureRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** The nightly jobs of the captures and the audit: what they delete, and that they leave a trace only when they do. */
class RetentionJobsAuditTest {

    private static final Instant NOW = Instant.parse("2026-10-28T03:40:00Z");

    private final AuditService audit = mock(AuditService.class);

    @Test
    void theAuditIsDeletedAfterItsRetentionButNeverBefore31Days() {
        AuditEventRepository events = mock(AuditEventRepository.class);
        when(events.deleteOlderThan(any())).thenReturn(4);

        int deleted = new AuditRetentionJob(events, new AuditProperties(Duration.ofDays(7)), audit, new TestClock(NOW))
                .purge();

        assertThat(deleted).isEqualTo(4);
        verify(events).deleteOlderThan(NOW.minus(Duration.ofDays(31)));
        verify(audit).recordSystem(eq(AuditAction.RETENTION_PURGED), contains("31 días"));
    }

    @Test
    void finishedCapturesOlderThanTheirRetentionAreDeletedButNotTheOnesInProgress() {
        TrafficCaptureRepository captures = mock(TrafficCaptureRepository.class);
        when(captures.deleteFinishedBefore(any(), any())).thenReturn(2);

        new CaptureRetentionJob(captures, CaptureFixtures.PROPERTIES, audit, new TestClock(NOW)).purge();

        verify(captures).deleteFinishedBefore(NOW.minus(CaptureFixtures.PROPERTIES.retention()), CaptureQueue.ACTIVE);
        verify(audit).recordSystem(eq(AuditAction.RETENTION_PURGED), contains("2 capturas"));
    }

    @Test
    void aNightWithNothingToDeleteLeavesNoTrace() {
        TrafficCaptureRepository captures = mock(TrafficCaptureRepository.class);
        AuditEventRepository events = mock(AuditEventRepository.class);

        new CaptureRetentionJob(captures, CaptureFixtures.PROPERTIES, audit, new TestClock(NOW)).purge();
        new AuditRetentionJob(events, new AuditProperties(Duration.ofDays(365)), audit, new TestClock(NOW)).purge();

        verifyNoInteractions(audit);
    }
}
