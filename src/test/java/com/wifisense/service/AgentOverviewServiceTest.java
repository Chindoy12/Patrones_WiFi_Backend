package com.wifisense.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifisense.dto.AgentOverview;
import com.wifisense.model.*;
import com.wifisense.repository.AgentAlertRepository;
import com.wifisense.repository.AgentDeviceRepository;
import com.wifisense.repository.AgentKeyRepository;
import com.wifisense.repository.AgentReportRepository;
import com.wifisense.repository.HomeNetworkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AgentOverviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");

    private final HomeNetworkRepository networks = mock(HomeNetworkRepository.class);
    private final AgentKeyRepository keys = mock(AgentKeyRepository.class);
    private final AgentReportRepository reports = mock(AgentReportRepository.class);
    private final AgentDeviceRepository devices = mock(AgentDeviceRepository.class);
    private final AgentAlertRepository alerts = mock(AgentAlertRepository.class);
    private final AgentOverviewService service = new AgentOverviewService(networks, keys, reports, devices, alerts,
            new AgentProperties(Duration.ofSeconds(50), Duration.ofDays(30), Duration.ofMinutes(15), 20, 48),
            new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    private final HomeNetwork casa = new HomeNetwork(mock(User.class), "Casa", null, null);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(casa, "id", 7L);
        when(networks.findByIdAndOwner_Username(7L, "ana")).thenReturn(Optional.of(casa));
        when(alerts.findByNetworkIdOrderByCreatedAtDesc(eq(7L), any())).thenReturn(List.of());
    }

    private AgentReport report(Instant at, int devicesCount) {
        AgentReport report = new AgentReport(casa, at, "1.0.0");
        report.devices(devicesCount, 0);
        report.details("{\"openPorts\":[80]}");
        return report;
    }

    @Test
    void withoutAgentThereIsNothingToShowYet() {
        AgentOverview overview = service.overview("ana", 7L);

        assertThat(overview.key()).isNull();
        assertThat(overview.online()).isFalse();
        assertThat(overview.latest()).isNull();
        assertThat(overview.devices()).isEmpty();
    }

    @Test
    void showsTheLatestReportHistoryInOrderAndWhichDevicesAreConnectedOrNew() {
        AgentKey key = new AgentKey(casa, "a".repeat(64), "wsa_AbC1");
        key.seen(NOW.minusSeconds(120));
        when(keys.findByNetworkId(7L)).thenReturn(Optional.of(key));
        Instant latestAt = NOW.minusSeconds(120);
        when(reports.findByNetworkIdOrderByReceivedAtDesc(eq(7L), any(Pageable.class)))
                .thenReturn(List.of(report(latestAt, 2), report(NOW.minusSeconds(420), 3)));
        AgentDevice router = new AgentDevice(7L, "a".repeat(24), NOW.minus(Duration.ofDays(3)));
        router.seen(latestAt);
        AgentDevice newcomer = new AgentDevice(7L, "b".repeat(24), NOW.minus(Duration.ofHours(2)));
        newcomer.seen(latestAt);
        AgentDevice gone = new AgentDevice(7L, "c".repeat(24), NOW.minus(Duration.ofDays(2)));
        gone.seen(NOW.minus(Duration.ofDays(1)));
        when(devices.findByNetworkIdOrderByFirstSeenAtAsc(7L)).thenReturn(List.of(router, gone, newcomer));

        AgentOverview overview = service.overview("ana", 7L);

        assertThat(overview.online()).isTrue();
        assertThat(overview.key().prefix()).isEqualTo("wsa_AbC1");
        assertThat(overview.latest().deviceCount()).isEqualTo(2);
        assertThat(overview.latest().details().path("openPorts").get(0).asInt()).isEqualTo(80);
        assertThat(overview.history()).extracting(AgentOverview.HistoryPoint::deviceCount).containsExactly(3, 2);
        assertThat(overview.devices()).extracting(AgentOverview.Device::online).containsExactly(true, true, false);
        assertThat(overview.devices()).filteredOn(AgentOverview.Device::recent)
                .extracting(AgentOverview.Device::id).containsExactly("b".repeat(24));
    }

    @Test
    void showsTheMakerAndNameAndPutsUnauthorizedDevicesFirst() {
        when(keys.findByNetworkId(7L)).thenReturn(Optional.of(new AgentKey(casa, "a".repeat(64), "wsa_AbC1")));
        AgentDevice mine = new AgentDevice(7L, "a".repeat(24), NOW.minus(Duration.ofDays(3)));
        mine.identify("Apple", "iPhone-de-Ana");
        mine.rename("Mi celular");
        mine.seen(NOW);
        AgentDevice stranger = new AgentDevice(7L, "c".repeat(24), NOW.minus(Duration.ofHours(1)));
        stranger.identify("Samsung", "Galaxy");
        stranger.setAuthorized(false);
        stranger.seen(NOW);
        when(devices.findByNetworkIdOrderByFirstSeenAtAsc(7L)).thenReturn(List.of(mine, stranger));

        var shown = service.overview("ana", 7L).devices();

        assertThat(shown).extracting(AgentOverview.Device::authorized).containsExactly(false, true);
        assertThat(shown.get(0).vendor()).isEqualTo("Samsung");
        assertThat(shown.get(1).label()).isEqualTo("Mi celular");
    }

    @Test
    void includesTheRecentAlerts() {
        when(alerts.findByNetworkIdOrderByCreatedAtDesc(eq(7L), any())).thenReturn(List.of(
                new com.wifisense.model.AgentAlert(casa, com.wifisense.model.AlertType.NEW_DEVICE, "Equipo nuevo", true)));

        assertThat(service.overview("ana", 7L).alerts()).singleElement()
                .satisfies(a -> assertThat(a.type()).isEqualTo("NEW_DEVICE"));
    }

    @Test
    void anAgentSilentForMoreThanTheWindowIsOffline() {
        AgentKey key = new AgentKey(casa, "a".repeat(64), "wsa_AbC1");
        key.seen(NOW.minus(Duration.ofMinutes(16)));
        when(keys.findByNetworkId(7L)).thenReturn(Optional.of(key));

        assertThat(service.overview("ana", 7L).online()).isFalse();
    }

    @Test
    void someoneElsesNetworkIsNotFound() {
        assertThatThrownBy(() -> service.overview("eva", 7L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
