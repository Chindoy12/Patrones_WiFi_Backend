package com.wifisense.service;

import com.wifisense.model.AuditAction;
import com.wifisense.model.AuditEvent;
import com.wifisense.model.AuditOutcome;
import com.wifisense.model.User;
import com.wifisense.repository.AuditEventRepository;
import com.wifisense.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T15:00:00Z");

    private final AuditEventRepository events = mock(AuditEventRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuditService audit = new AuditService(events, users, new TestClock(NOW));

    @AfterEach
    void clearSession() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void thePersonComesFromTheSessionWithTheirRole() {
        User admin = new User("Jefe.Local", "jefe@correo.co", "hash", User.Role.ADMIN);
        ReflectionTestUtils.setField(admin, "id", 7L);
        when(users.findByUsername("Jefe.Local")).thenReturn(Optional.of(admin));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("Jefe.Local", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        audit.record(AuditAction.CAPTURE_REQUESTED, AuditOutcome.SUCCESS,
                new AuditEvent.Target("CAPTURE", 3L, 9L), "  30 s, 5000 paquetes  ");

        AuditEvent saved = saved();
        assertThat(saved.getActorId()).isEqualTo(7L);
        assertThat(saved.getActorUsername()).isEqualTo("Jefe.Local");
        assertThat(saved.getActorRole()).isEqualTo("ADMIN");
        assertThat(saved.getTargetType()).isEqualTo("CAPTURE");
        assertThat(saved.getNetworkId()).isEqualTo(9L);
        assertThat(saved.getDetail()).isEqualTo("30 s, 5000 paquetes");
        assertThat(saved.getOccurredAt()).isEqualTo(NOW);
    }

    @Test
    void withoutASessionTheSystemIsTheActor() {
        audit.recordSystem(AuditAction.RETENTION_PURGED, "12 capturas de más de 90 días");

        AuditEvent saved = saved();
        assertThat(saved.getActorUsername()).isNull();
        assertThat(saved.getActorRole()).isEqualTo("SYSTEM");
        assertThat(saved.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
    }

    @Test
    void anAccountThatNoLongerExistsKeepsTheNameItUsed() {
        when(users.findByUsername(any())).thenReturn(Optional.empty());

        audit.recordAs("Eva.Borrada", AuditAction.USER_DELETED, AuditOutcome.FAILED, null, "No existe");

        assertThat(saved().getActorUsername()).isEqualTo("Eva.Borrada");
    }

    @Test
    void onlyTheSensitiveRefusalsAreRecorded() {
        assertThat(AuditService.deniedAction("POST", "/api/networks/4/sensor/captures"))
                .isEqualTo(AuditAction.CAPTURE_REQUESTED);
        assertThat(AuditService.deniedAction("GET", "/api/networks/4/sensor/captures/9"))
                .isEqualTo(AuditAction.CAPTURE_VIEWED);
        assertThat(AuditService.deniedAction("POST", "/api/networks/4/sensor/captures/9/cancel"))
                .isEqualTo(AuditAction.CAPTURE_CANCELLED);
        assertThat(AuditService.deniedAction("PATCH", "/api/users/3/role")).isEqualTo(AuditAction.USER_ROLE_CHANGED);
        assertThat(AuditService.deniedAction("DELETE", "/api/users/3")).isEqualTo(AuditAction.USER_DELETED);
        assertThat(AuditService.deniedAction("GET", "/api/users")).isNull();
        assertThat(AuditService.deniedAction("GET", "/api/networks/4/sensor/overview")).isNull();
        assertThat(AuditService.deniedAction(null, "/api/users")).isNull();

        audit.deniedRequest("Ana.Analista", "GET", "/api/alerts");
        verify(events, never()).save(any());
        audit.deniedRequest("Ana.Analista", "POST", "/api/networks/4/sensor/captures");
        assertThat(saved().getOutcome()).isEqualTo(AuditOutcome.DENIED);
    }

    @Test
    void aFailureWritingTheAuditNeverBreaksTheOperation() {
        when(events.save(any())).thenThrow(new IllegalStateException("la base no responde"));

        assertThatCode(() -> audit.recordSystem(AuditAction.RETENTION_PURGED, "1 registro")).doesNotThrowAnyException();
    }

    private AuditEvent saved() {
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(events).save(event.capture());
        return event.getValue();
    }
}
