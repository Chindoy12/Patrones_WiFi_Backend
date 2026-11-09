package com.wifisense.audit;

import com.wifisense.dto.UserResponse;
import com.wifisense.model.AuditAction;
import com.wifisense.model.AuditEvent.Target;
import com.wifisense.model.AuditOutcome;
import com.wifisense.service.AuditService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * <b>Aspect (cross-cutting Observer)</b>: leaves an audit trace of the sensitive operations that already existed
 * (accounts, roles, devices marked as not authorized, sensor and agent keys, team networks) without touching their
 * services.
 * <p>
 * Problem: those operations live in eight services with many callers and tests; writing the audit inside each one
 * would mix it with their logic and change every constructor. Solution: this aspect observes their public methods
 * and records SUCCESS when they return or FAILED when they throw. It runs outside their transaction (order 0), so a
 * SUCCESS is recorded only after the change was committed. The person comes from the session; the details never hold
 * a password, a key, a code, a MAC or the name of an end user's device.
 */
@Aspect
@Component
@Order(0)
public class SensitiveOperationAudit {

    private final AuditService audit;

    public SensitiveOperationAudit(AuditService audit) {
        this.audit = audit;
    }

    // ------------------------------------------------------------------------------------------- accounts and roles

    @AfterReturning(pointcut = "execution(* com.wifisense.service.UserService.create(..))", returning = "user")
    public void userCreated(UserResponse user) {
        audit.record(AuditAction.USER_CREATED, AuditOutcome.SUCCESS, new Target("USER", user.id(), null),
                "Cuenta " + user.username() + " con rol " + user.role());
    }

    @AfterReturning(pointcut = "execution(* com.wifisense.service.UserService.changeRole(..))", returning = "user")
    public void roleChanged(UserResponse user) {
        audit.record(AuditAction.USER_ROLE_CHANGED, AuditOutcome.SUCCESS, new Target("USER", user.id(), null),
                "Cuenta " + user.username() + ": nuevo rol " + user.role());
    }

    @AfterReturning("execution(* com.wifisense.service.UserService.delete(..)) && args(id, ..)")
    public void userDeleted(Long id) {
        audit.record(AuditAction.USER_DELETED, AuditOutcome.SUCCESS, new Target("USER", id, null), null);
    }

    @AfterThrowing(pointcut = "execution(* com.wifisense.service.UserService.create(..)) "
            + "|| execution(* com.wifisense.service.UserService.changeRole(..)) "
            + "|| execution(* com.wifisense.service.UserService.delete(..))", throwing = "error")
    public void accountFailed(JoinPoint point, RuntimeException error) {
        AuditAction action = switch (point.getSignature().getName()) {
            case "create" -> AuditAction.USER_CREATED;
            case "changeRole" -> AuditAction.USER_ROLE_CHANGED;
            default -> AuditAction.USER_DELETED;
        };
        Object first = point.getArgs().length > 0 ? point.getArgs()[0] : null;
        Target target = first instanceof Long id ? new Target("USER", id, null) : null;
        audit.record(action, AuditOutcome.FAILED, target, error.getMessage());
    }

    // ----------------------------------------------------------------------------------------- devices of a network

    /** A team network's device (only an ADMIN can do it): the label and a fingerprint prefix help find it. */
    @AfterReturning("execution(* com.wifisense.service.SensorDeviceService.update(..)) "
            + "&& args(networkId, fingerprint, label, authorized)")
    public void teamDevice(Long networkId, String fingerprint, String label, Boolean authorized) {
        if (authorized != null) {
            audit.record(authorized ? AuditAction.DEVICE_UNBLOCKED : AuditAction.DEVICE_BLOCKED, AuditOutcome.SUCCESS,
                    new Target("NETWORK", networkId, networkId), "Equipo " + shortId(fingerprint)
                            + (label == null || label.isBlank() ? "" : " («" + label.strip() + "»)"));
        }
    }

    /** An end user's own device: recorded without its name or fingerprint, which are private to the user. */
    @AfterReturning("execution(* com.wifisense.service.AgentDeviceService.update(..)) "
            + "&& args(username, networkId, fingerprint, label, authorized)")
    public void homeDevice(String username, Long networkId, String fingerprint, String label, Boolean authorized) {
        if (authorized != null) {
            audit.record(authorized ? AuditAction.DEVICE_UNBLOCKED : AuditAction.DEVICE_BLOCKED, AuditOutcome.SUCCESS,
                    new Target("HOME_NETWORK", networkId, null), "Un equipo de su red de Mi Wi-Fi");
        }
    }

    // --------------------------------------------------------------------------------------- sensors and agents keys

    @AfterReturning("execution(* com.wifisense.service.NetworkSensorService.createPairingCode(..)) && args(networkId)")
    public void sensorCode(Long networkId) {
        audit.record(AuditAction.SENSOR_PAIRING_CREATED, AuditOutcome.SUCCESS,
                new Target("NETWORK", networkId, networkId), null);
    }

    @AfterReturning("execution(* com.wifisense.service.NetworkSensorService.revoke(..)) && args(networkId)")
    public void sensorRevoked(Long networkId) {
        audit.record(AuditAction.SENSOR_REVOKED, AuditOutcome.SUCCESS, new Target("NETWORK", networkId, networkId),
                null);
    }

    @AfterReturning("execution(* com.wifisense.service.AgentPairingService.create(..)) && args(username, networkId)")
    public void agentCode(String username, Long networkId) {
        audit.record(AuditAction.AGENT_PAIRING_CREATED, AuditOutcome.SUCCESS,
                new Target("HOME_NETWORK", networkId, null), null);
    }

    @AfterReturning("execution(* com.wifisense.service.AgentKeyService.revoke(..)) && args(username, networkId)")
    public void agentRevoked(String username, Long networkId) {
        audit.record(AuditAction.AGENT_KEY_REVOKED, AuditOutcome.SUCCESS, new Target("HOME_NETWORK", networkId, null),
                null);
    }

    // ------------------------------------------------------------------------------------------------ team networks

    @AfterReturning("execution(* com.wifisense.service.NetworkService.delete(..)) && args(id)")
    public void networkDeleted(Long id) {
        audit.record(AuditAction.NETWORK_DELETED, AuditOutcome.SUCCESS, new Target("NETWORK", id, id), null);
    }

    static String shortId(String fingerprint) {
        return fingerprint == null ? "?" : fingerprint.length() > 8 ? fingerprint.substring(0, 8) : fingerprint;
    }
}
