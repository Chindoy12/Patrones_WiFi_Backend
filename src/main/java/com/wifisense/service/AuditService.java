package com.wifisense.service;

import com.wifisense.model.AuditAction;
import com.wifisense.model.AuditEvent;
import com.wifisense.model.AuditEvent.Actor;
import com.wifisense.model.AuditEvent.Target;
import com.wifisense.model.AuditOutcome;
import com.wifisense.model.User;
import com.wifisense.repository.AuditEventRepository;
import com.wifisense.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Leaves a trace of each sensitive operation. The person comes from the session (never from the request body), each
 * row is written in its own transaction (a refused or failed operation that rolls back still leaves its trace), and
 * a problem writing the audit is logged but never breaks the operation itself.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository events;
    private final UserRepository users;
    private final Clock clock;

    public AuditService(AuditEventRepository events, UserRepository users, Clock clock) {
        this.events = events;
        this.users = users;
        this.clock = clock;
    }

    /** An operation of the person of the current session. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditAction action, AuditOutcome outcome, Target target, String detail) {
        save(currentActor(), action, outcome, target, detail);
    }

    /** An operation of the person with this username (when the session is not at hand, for example a filter). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAs(String username, AuditAction action, AuditOutcome outcome, Target target, String detail) {
        save(actorOf(username), action, outcome, target, detail);
    }

    /** An automatic task of the system (the nightly retention, for example). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSystem(AuditAction action, String detail) {
        save(Actor.SYSTEM, action, AuditOutcome.SUCCESS, null, detail);
    }

    /**
     * A request the backend refused for lack of permission (called by the access-denied handler). Only the sensitive
     * ones are recorded: the technical captures and the management of accounts.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deniedRequest(String username, String method, String path) {
        AuditAction action = deniedAction(method, path);
        if (action != null) {
            save(actorOf(username), action, AuditOutcome.DENIED, null, "Sin permiso: " + method + " " + path);
        }
    }

    static AuditAction deniedAction(String method, String path) {
        if (method == null || path == null) {
            return null;
        }
        if (path.matches("^/api/networks/\\d+/sensor/captures(/.*)?$")) {
            if (path.endsWith("/cancel")) {
                return AuditAction.CAPTURE_CANCELLED;
            }
            return "POST".equals(method) ? AuditAction.CAPTURE_REQUESTED : AuditAction.CAPTURE_VIEWED;
        }
        if (path.startsWith("/api/users")) {
            return switch (method) {
                case "POST" -> AuditAction.USER_CREATED;
                case "PATCH" -> AuditAction.USER_ROLE_CHANGED;
                case "DELETE" -> AuditAction.USER_DELETED;
                default -> null;
            };
        }
        return null;
    }

    private void save(Actor actor, AuditAction action, AuditOutcome outcome, Target target, String detail) {
        try {
            events.save(new AuditEvent(Instant.now(clock), actor, action, outcome, target, detail));
        } catch (RuntimeException e) {
            log.error("Audit of {} ({}) not written: {}", action, outcome, e.getMessage());
        }
    }

    Actor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            return Actor.SYSTEM;
        }
        return actorOf(authentication.getName());
    }

    private Actor actorOf(String username) {
        if (username == null || username.isBlank()) {
            return Actor.SYSTEM;
        }
        return users.findByUsername(username)
                .map(user -> new Actor(user.getId(), user.getUsername(), roleOf(user)))
                .orElse(new Actor(null, username.length() > 100 ? username.substring(0, 100) : username, null));
    }

    private static String roleOf(User user) {
        return user.getRole() == null ? null : user.getRole().name();
    }
}
