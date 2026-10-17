package com.wifisense.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifisense.dto.TeamNetworksOverview;
import com.wifisense.model.AgentKey;
import com.wifisense.model.AgentReport;
import com.wifisense.model.HomeNetwork;
import com.wifisense.model.User;
import com.wifisense.repository.AgentKeyRepository;
import com.wifisense.repository.AgentReportRepository;
import com.wifisense.repository.HomeNetworkRepository;
import com.wifisense.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeamNetworkServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T15:00:00Z");

    private final HomeNetworkRepository networks = mock(HomeNetworkRepository.class);
    private final AgentKeyRepository keys = mock(AgentKeyRepository.class);
    private final AgentReportRepository reports = mock(AgentReportRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TeamNetworkService service = new TeamNetworkService(networks, keys, reports, users,
            new AgentProperties(Duration.ofSeconds(50), Duration.ofDays(30), Duration.ofMinutes(15), 20, 48),
            new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

    private final HomeNetwork calm = network(1L, "ana.lopez@gmail.com", "Casa Ana");
    private final HomeNetwork risky = network(2L, "carlos", "Apto Carlos");
    private final HomeNetwork noAgent = network(3L, "al", "Finca");

    private static HomeNetwork network(long id, String owner, String name) {
        HomeNetwork network = new HomeNetwork(new User(owner, null, "hash", User.Role.USER), name, "Claro", 300);
        ReflectionTestUtils.setField(network, "id", id);
        return network;
    }

    private static AgentKey key(HomeNetwork network, Instant seen) {
        AgentKey key = new AgentKey(network, "f".repeat(64), "wsa_abcd");
        key.seen(seen);
        return key;
    }

    private static AgentReport report(long id, HomeNetwork network, Instant at, int devices, String grade,
                                      Boolean anomaly, String details) {
        AgentReport report = new AgentReport(network, at, "1.3.0");
        ReflectionTestUtils.setField(report, "id", id);
        report.wifi("Casa", "WPA2", "2.4", 6, -60);
        report.devices(devices, 0);
        report.security(grade.equals("SECURE") ? 95 : 50, grade);
        report.quality(80);
        if (anomaly != null) {
            report.anomaly(anomaly, anomaly ? "CONGESTION" : "NONE");
        }
        report.details(details);
        return report;
    }

    @BeforeEach
    void setUp() {
        when(networks.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(calm, risky, noAgent));
        when(keys.findAll()).thenReturn(List.of(key(calm, NOW.minusSeconds(120)),
                key(risky, NOW.minus(Duration.ofHours(3)))));
        when(reports.findLatestOfEachNetwork()).thenReturn(List.of(
                report(10L, calm, NOW.minusSeconds(120), 5, "SECURE", false,
                        "{\"channel\":{\"recommendedChannel\":11,\"shouldChange\":true,\"summary\":\"x\"}}"),
                report(11L, risky, NOW.minus(Duration.ofHours(3)), 2, "AT_RISK", true, "{}")));
        when(users.countByRoleIn(Set.of(User.Role.USER))).thenReturn(4L);
        when(reports.countNetworksWithAnomalySince(any())).thenReturn(1L);
    }

    @Test
    void countsTheRealNetworksOfTheEndUsers() {
        TeamNetworksOverview.Totals totals = service.overview(true).totals();

        assertThat(totals.endUsers()).isEqualTo(4);
        assertThat(totals.networks()).isEqualTo(3);
        assertThat(totals.withAgent()).isEqualTo(2);
        assertThat(totals.agentsOnline()).isEqualTo(1);  // the other one reported 3 hours ago
        assertThat(totals.atRisk()).isEqualTo(1);
        assertThat(totals.anomaliesLast24h()).isEqualTo(1);
        assertThat(totals.devices()).isEqualTo(7);
    }

    @Test
    void networksWithAProblemComeFirstAndThoseWithoutAgentLast() {
        List<TeamNetworksOverview.NetworkRow> rows = service.overview(true).networks();

        assertThat(rows).extracting(TeamNetworksOverview.NetworkRow::name)
                .containsExactly("Apto Carlos", "Casa Ana", "Finca");
        assertThat(rows.get(0).needsAttention()).isTrue();
        assertThat(rows.get(2).hasAgent()).isFalse();
        assertThat(rows.get(2).lastReportAt()).isNull();
    }

    @Test
    void readsTheChannelRecommendationOfTheLatestReport() {
        TeamNetworksOverview.NetworkRow ana = service.overview(true).networks().get(1);

        assertThat(ana.channel()).isEqualTo(6);
        assertThat(ana.recommendedChannel()).isEqualTo(11);
        assertThat(ana.channelChangeSuggested()).isTrue();
        assertThat(ana.agentOnline()).isTrue();
        assertThat(ana.deviceCount()).isEqualTo(5);
    }

    @Test
    void onlyAnAdministratorSeesWhoseNetworkItIs() {
        assertThat(service.overview(true).networks()).extracting(TeamNetworksOverview.NetworkRow::owner)
                .containsExactly("carlos", "ana.lopez@gmail.com", "al");
        assertThat(service.overview(false).networks()).extracting(TeamNetworksOverview.NetworkRow::owner)
                .containsExactly("ca•••", "an•••@gmail.com", "a•••");
    }

    @Test
    void withNoNetworksTheTotalsAreZero() {
        when(networks.findAllByOrderByCreatedAtAsc()).thenReturn(List.of());
        when(keys.findAll()).thenReturn(List.of());
        when(reports.findLatestOfEachNetwork()).thenReturn(List.of());
        when(reports.countNetworksWithAnomalySince(any())).thenReturn(0L);

        TeamNetworksOverview overview = service.overview(true);

        assertThat(overview.networks()).isEmpty();
        assertThat(overview.totals().networks()).isZero();
        assertThat(overview.generatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @CsvSource({"ana.lopez@gmail.com, an•••@gmail.com", "carlos, ca•••", "al, a•••", "x@y.co, x•••@y.co"})
    void masksTheOwner(String username, String masked) {
        assertThat(TeamNetworkService.mask(username)).isEqualTo(masked);
    }
}
