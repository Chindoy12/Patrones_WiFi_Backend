package com.wifisense.service;

import com.wifisense.dto.AuditPage;
import com.wifisense.dto.RetentionPolicyResponse;
import com.wifisense.model.AuditAction;
import com.wifisense.model.AuditEvent;
import com.wifisense.model.AuditOutcome;
import com.wifisense.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class AuditQueryServiceTest {

    private final AuditEventRepository events = mock(AuditEventRepository.class);
    private final AuditQueryService service = new AuditQueryService(events, new AuditProperties(Duration.ofDays(10)),
            CaptureFixtures.PROPERTIES, WifiScanQueueTest.PROPERTIES,
            new AgentProperties(Duration.ofSeconds(50), Duration.ofDays(30), Duration.ofMinutes(15), 20, 48),
            new RetentionProperties(Duration.ofDays(90), 5000));

    @Test
    void filtersByActionAndOutcomeAndNeverAsksForAHugePage() {
        AuditEvent event = new AuditEvent(Instant.parse("2026-10-28T15:00:00Z"),
                new AuditEvent.Actor(1L, "Jefe.Local", "ADMIN"), AuditAction.CAPTURE_REQUESTED, AuditOutcome.DENIED,
                null, "Sin permiso");
        when(events.search(eq(AuditAction.CAPTURE_REQUESTED), eq(AuditOutcome.DENIED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event), PageRequest.of(0, 100), 1));

        AuditPage page = service.search(" CAPTURE_REQUESTED ", "DENIED", -3, 5000);

        verify(events).search(AuditAction.CAPTURE_REQUESTED, AuditOutcome.DENIED, PageRequest.of(0, 100));
        assertThat(page.size()).isEqualTo(AuditQueryService.MAX_PAGE_SIZE);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
    }

    @Test
    void noFilterMeansEverythingAndAnUnknownFilterIsAnError() {
        when(events.search(isNull(), isNull(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        assertThat(service.search(null, " ", 0, 20).items()).isEmpty();

        assertThatThrownBy(() -> service.search("BORRAR_TODO", null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("BORRAR_TODO");
    }

    @Test
    void theAuditIsKeptAtLeastAsLongAsTheTableProtectsIt() {
        RetentionPolicyResponse audit = service.policies().stream().filter(p -> p.code().equals("AUDIT")).findFirst()
                .orElseThrow();

        assertThat(audit.days()).isEqualTo(31L);  // 10 days were configured: the table refuses younger deletions
    }

    @Test
    void theCaptureFilesNeverLeaveTheComputerOfTheSensor() {
        RetentionPolicyResponse files = service.policies().getFirst();

        assertThat(files.code()).isEqualTo("CAPTURE_FILES");
        assertThat(files.days()).isEqualTo(0L);
        assertThat(files.afterwards()).contains("Nunca sale del computador");
        assertThat(service.actions()).hasSize(AuditAction.values().length);
    }
}
