package com.wifisense.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * One sensitive operation: who (the username is copied, so it survives the account), what, on what, with which
 * result and a short detail without secrets. Rows are only inserted: the entity is immutable here and the table
 * rejects every change.
 */
@Entity
@Immutable
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_username", length = 100)
    private String actorUsername;

    @Column(name = "actor_role", length = 8)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private AuditOutcome outcome;

    @Column(name = "target_type", length = 20)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "network_id")
    private Long networkId;

    @Column(length = 300)
    private String detail;

    protected AuditEvent() {
    }

    public AuditEvent(Instant occurredAt, Actor actor, AuditAction action, AuditOutcome outcome, Target target,
                      String detail) {
        this.occurredAt = occurredAt;
        this.actorId = actor.id();
        this.actorUsername = actor.username();
        this.actorRole = actor.role();
        this.action = action;
        this.outcome = outcome;
        if (target != null) {
            this.targetType = target.type();
            this.targetId = target.id();
            this.networkId = target.networkId();
        }
        this.detail = detail == null || detail.isBlank() ? null
                : detail.strip().length() > 300 ? detail.strip().substring(0, 299) + "…" : detail.strip();
    }

    /** Who did it: an account, or the system itself (id and username null, role SYSTEM). */
    public record Actor(Long id, String username, String role) {
        public static final Actor SYSTEM = new Actor(null, null, "SYSTEM");
    }

    /** On what: its type (CAPTURE, USER, DEVICE...), its id and the network it belongs to, if any. */
    public record Target(String type, Long id, Long networkId) {
    }

    public Long getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public Long getActorId() { return actorId; }
    public String getActorUsername() { return actorUsername; }
    public String getActorRole() { return actorRole; }
    public AuditAction getAction() { return action; }
    public AuditOutcome getOutcome() { return outcome; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public Long getNetworkId() { return networkId; }
    public String getDetail() { return detail; }
}
