package com.wifisense.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The team panel with real data: every home network registered by an end user, whether its agent is connected and
 * what the AI said in its latest report. Four queries in total, whatever the number of networks.
 */
@Service
public class TeamNetworkService {

    static final Duration ANOMALY_WINDOW = Duration.ofHours(24);
    static final Set<String> RISKY_GRADES = Set.of("AT_RISK", "CRITICAL");

    private final HomeNetworkRepository networks;
    private final AgentKeyRepository keys;
    private final AgentReportRepository reports;
    private final UserRepository users;
    private final AgentProperties properties;
    private final ObjectMapper json;
    private final Clock clock;

    public TeamNetworkService(HomeNetworkRepository networks, AgentKeyRepository keys, AgentReportRepository reports,
                              UserRepository users, AgentProperties properties, ObjectMapper json, Clock clock) {
        this.networks = networks;
        this.keys = keys;
        this.reports = reports;
        this.users = users;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    /** {@code revealOwners}: only an administrator sees whose network each one is; an analyst sees it masked. */
    @Transactional(readOnly = true)
    public TeamNetworksOverview overview(boolean revealOwners) {
        Instant now = Instant.now(clock);
        Map<Long, AgentKey> keyByNetwork = new HashMap<>();
        keys.findAll().forEach(k -> keyByNetwork.put(k.getNetwork().getId(), k));
        Map<Long, AgentReport> latestByNetwork = new HashMap<>();
        for (AgentReport report : reports.findLatestOfEachNetwork()) {
            // two reports with the same timestamp: keep the one saved last
            latestByNetwork.merge(report.getNetwork().getId(), report, (a, b) -> a.getId() > b.getId() ? a : b);
        }

        List<TeamNetworksOverview.NetworkRow> rows = networks.findAllByOrderByCreatedAtAsc().stream()
                .map(n -> row(n, keyByNetwork.get(n.getId()), latestByNetwork.get(n.getId()), now, revealOwners))
                .sorted(Comparator.comparing(TeamNetworksOverview.NetworkRow::needsAttention).reversed()
                        .thenComparing(TeamNetworksOverview.NetworkRow::agentOnline, Comparator.reverseOrder())
                        .thenComparing(TeamNetworksOverview.NetworkRow::lastReportAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        TeamNetworksOverview.Totals totals = new TeamNetworksOverview.Totals(
                users.countByRoleIn(Set.of(User.Role.USER)),
                rows.size(),
                (int) rows.stream().filter(TeamNetworksOverview.NetworkRow::hasAgent).count(),
                (int) rows.stream().filter(TeamNetworksOverview.NetworkRow::agentOnline).count(),
                (int) rows.stream().filter(r -> r.securityGrade() != null && RISKY_GRADES.contains(r.securityGrade()))
                        .count(),
                reports.countNetworksWithAnomalySince(now.minus(ANOMALY_WINDOW)),
                rows.stream().mapToInt(r -> r.deviceCount() == null ? 0 : r.deviceCount()).sum());
        return new TeamNetworksOverview(totals, rows, now);
    }

    private TeamNetworksOverview.NetworkRow row(HomeNetwork network, AgentKey key, AgentReport latest, Instant now,
                                                boolean revealOwners) {
        boolean online = key != null && key.getLastSeenAt() != null
                && key.getLastSeenAt().isAfter(now.minus(properties.onlineWindow()));
        String username = network.getOwner().getUsername();
        String owner = revealOwners ? username : mask(username);
        if (latest == null) {
            return new TeamNetworksOverview.NetworkRow(network.getId(), network.getName(), owner, network.getProvider(),
                    network.getContractedMbps(), network.getCreatedAt(), key != null, online, null, null, null, null,
                    null, null, null, null, null, null, null, false, false);
        }
        JsonNode channel = details(latest.getDetails()).path("channel");
        Integer recommended = channel.path("recommendedChannel").isInt() ? channel.get("recommendedChannel").asInt()
                : null;
        boolean changeChannel = channel.path("shouldChange").asBoolean(false);
        boolean attention = Boolean.TRUE.equals(latest.getAnomalyDetected())
                || (latest.getSecurityGrade() != null && RISKY_GRADES.contains(latest.getSecurityGrade()));
        return new TeamNetworksOverview.NetworkRow(network.getId(), network.getName(), owner, network.getProvider(),
                network.getContractedMbps(), network.getCreatedAt(), key != null, online, latest.getReceivedAt(),
                latest.getAgentVersion(), latest.getDeviceCount(), latest.getSecurityScore(),
                latest.getSecurityGrade(), latest.getQualityScore(), latest.getAnomalyDetected(),
                latest.getAnomalyType(), latest.getBand(), latest.getChannel(), recommended, changeChannel, attention);
    }

    /** "ana.lopez@gmail.com" -> "an•••@gmail.com", "carlos" -> "ca•••", "al" -> "a•••". */
    static String mask(String username) {
        int at = username.indexOf('@');
        String local = at < 0 ? username : username.substring(0, at);
        String domain = at < 0 ? "" : username.substring(at);
        int visible = local.length() <= 3 ? 1 : 2;
        return local.substring(0, Math.min(visible, local.length())) + "•••" + domain;
    }

    private JsonNode details(String text) {
        try {
            return json.readTree(text == null ? "{}" : text);
        } catch (JsonProcessingException e) {
            return json.createObjectNode();
        }
    }
}
