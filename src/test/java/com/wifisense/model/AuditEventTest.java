package com.wifisense.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventTest {

    private static final Instant AT = Instant.parse("2026-10-28T15:00:00Z");

    @Test
    void aLongDetailIsCutToWhatTheTableKeeps() {
        AuditEvent event = new AuditEvent(AT, AuditEvent.Actor.SYSTEM, AuditAction.RETENTION_PURGED,
                AuditOutcome.SUCCESS, null, "x".repeat(500));

        assertThat(event.getDetail()).hasSize(300).endsWith("…");
    }

    @Test
    void anEmptyDetailIsNoDetailAndNoTargetLeavesItsColumnsEmpty() {
        AuditEvent event = new AuditEvent(AT, new AuditEvent.Actor(1L, "Jefe", "ADMIN"), AuditAction.USER_DELETED,
                AuditOutcome.SUCCESS, null, "   ");

        assertThat(event.getDetail()).isNull();
        assertThat(event.getTargetType()).isNull();
        assertThat(event.getTargetId()).isNull();
        assertThat(event.getNetworkId()).isNull();
    }

    @Test
    void everyActionHasALabelForTheAuditPage() {
        for (AuditAction action : AuditAction.values()) {
            assertThat(action.label()).isNotBlank();
            assertThat(action.name()).matches("^[A-Z][A-Z_]{2,31}$");  // what the table accepts
        }
    }
}
