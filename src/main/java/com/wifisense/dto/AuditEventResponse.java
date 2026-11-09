package com.wifisense.dto;

import com.wifisense.model.AuditEvent;

import java.time.Instant;

/** One row of the audit, with the label of its action in Spanish. */
public record AuditEventResponse(Long id, Instant occurredAt, String actorUsername, String actorRole, String action,
                                 String actionLabel, String outcome, String targetType, Long targetId,
                                 Long networkId, String detail) {

    public static AuditEventResponse from(AuditEvent e) {
        return new AuditEventResponse(e.getId(), e.getOccurredAt(), e.getActorUsername(), e.getActorRole(),
                e.getAction().name(), e.getAction().label(), e.getOutcome().name(), e.getTargetType(),
                e.getTargetId(), e.getNetworkId(), e.getDetail());
    }
}
