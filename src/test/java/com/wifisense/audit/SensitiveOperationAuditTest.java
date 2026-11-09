package com.wifisense.audit;

import com.wifisense.dto.UserResponse;
import com.wifisense.model.AuditAction;
import com.wifisense.model.AuditEvent;
import com.wifisense.model.AuditOutcome;
import com.wifisense.service.AuditService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class SensitiveOperationAuditTest {

    private final AuditService audit = mock(AuditService.class);
    private final SensitiveOperationAudit aspect = new SensitiveOperationAudit(audit);

    @Test
    void aBlockedTeamDeviceIsRecordedWithItsLabelAndOnlyTheStartOfItsFingerprint() {
        aspect.teamDevice(4L, "0123456789abcdef01234567", " Portátil del laboratorio ", false);

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).record(eq(AuditAction.DEVICE_BLOCKED), eq(AuditOutcome.SUCCESS),
                eq(new AuditEvent.Target("NETWORK", 4L, 4L)), detail.capture());
        assertThat(detail.getValue()).isEqualTo("Equipo 01234567 («Portátil del laboratorio»)");
    }

    @Test
    void renamingADeviceWithoutChangingItsPermissionIsNotSensitive() {
        aspect.teamDevice(4L, "0123456789abcdef01234567", "Impresora", null);
        aspect.homeDevice("ana", 8L, "0123456789abcdef01234567", "Celular de Ana", null);

        verifyNoInteractions(audit);
    }

    @Test
    void theDeviceOfAnEndUserIsRecordedWithoutItsNameOrFingerprint() {
        aspect.homeDevice("ana", 8L, "0123456789abcdef01234567", "Celular de Ana", true);

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).record(eq(AuditAction.DEVICE_UNBLOCKED), eq(AuditOutcome.SUCCESS),
                eq(new AuditEvent.Target("HOME_NETWORK", 8L, null)), detail.capture());
        assertThat(detail.getValue()).doesNotContain("Ana").doesNotContain("0123");
    }

    @Test
    void anAccountOperationThatFailsIsRecordedAsFailedWithItsReason() {
        JoinPoint point = mock(JoinPoint.class);
        Signature signature = mock(Signature.class);
        when(point.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("changeRole");
        when(point.getArgs()).thenReturn(new Object[]{12L, "VIEWER"});

        aspect.accountFailed(point, new IllegalArgumentException("No puedes cambiar tu propio rol."));

        verify(audit).record(AuditAction.USER_ROLE_CHANGED, AuditOutcome.FAILED,
                new AuditEvent.Target("USER", 12L, null), "No puedes cambiar tu propio rol.");
    }

    @Test
    void aCreatedAccountSaysItsRoleButNeverItsPassword() {
        UserResponse user = new UserResponse(5L, "Eva.Equipo", com.wifisense.model.User.Role.ANALYST);

        aspect.userCreated(user);

        verify(audit).record(eq(AuditAction.USER_CREATED), eq(AuditOutcome.SUCCESS),
                eq(new AuditEvent.Target("USER", 5L, null)), eq("Cuenta Eva.Equipo con rol ANALYST"));
    }

    @Test
    void keysAndCodesAreRecordedWithoutTheirValue() {
        aspect.sensorCode(3L);
        aspect.agentRevoked("ana", 8L);

        verify(audit).record(AuditAction.SENSOR_PAIRING_CREATED, AuditOutcome.SUCCESS,
                new AuditEvent.Target("NETWORK", 3L, 3L), null);
        verify(audit).record(eq(AuditAction.AGENT_KEY_REVOKED), eq(AuditOutcome.SUCCESS), any(), isNull());
    }

    @Test
    void aShortFingerprintIsKeptWhole() {
        assertThat(SensitiveOperationAudit.shortId("abc")).isEqualTo("abc");
        assertThat(SensitiveOperationAudit.shortId(null)).isEqualTo("?");
    }
}
