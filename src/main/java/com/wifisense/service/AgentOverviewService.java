package com.wifisense.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifisense.dto.AgentOverview;
import com.wifisense.model.AgentDevice;
import com.wifisense.model.AgentKey;
import com.wifisense.model.AgentReport;
import com.wifisense.repository.AgentAlertRepository;
import com.wifisense.repository.AgentDeviceRepository;
import com.wifisense.repository.AgentKeyRepository;
import com.wifisense.repository.AgentReportRepository;
import com.wifisense.repository.HomeNetworkRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** What Mi Wi-Fi shows about the agent of one network: key, latest report, history and devices. */
@Service
public class AgentOverviewService {

    static final Duration RECENT_DEVICE = Duration.ofHours(24);

    static final int ALERTS_SHOWN = 10;

    private final HomeNetworkRepository networks;
    private final AgentKeyRepository keys;
    private final AgentReportRepository reports;
    private final AgentDeviceRepository devices;
    private final AgentAlertRepository alerts;
    private final AgentProperties properties;
    private final ObjectMapper json;
    private final Clock clock;

    public AgentOverviewService(HomeNetworkRepository networks, AgentKeyRepository keys, AgentReportRepository reports,
                                AgentDeviceRepository devices, AgentAlertRepository alerts, AgentProperties properties,
                                ObjectMapper json, Clock clock) {
        this.networks = networks;
        this.keys = keys;
        this.reports = reports;
        this.devices = devices;
        this.alerts = alerts;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AgentOverview overview(String username, Long networkId) {
        networks.findByIdAndOwner_Username(networkId, username)
                .orElseThrow(() -> new ResourceNotFoundException("la red", networkId));
        Instant now = Instant.now(clock);
        AgentKey key = keys.findByNetworkId(networkId).orElse(null);
        List<AgentReport> recent = reports.findByNetworkIdOrderByReceivedAtDesc(networkId,
                PageRequest.of(0, Math.max(properties.historySize(), 1)));
        AgentReport latest = recent.isEmpty() ? null : recent.get(0);

        boolean online = key != null && key.getLastSeenAt() != null
                && key.getLastSeenAt().isAfter(now.minus(properties.onlineWindow()));
        List<AgentOverview.HistoryPoint> history = recent.stream()
                .sorted(Comparator.comparing(AgentReport::getReceivedAt))
                .map(r -> new AgentOverview.HistoryPoint(r.getReceivedAt(), r.getLatencyMs(), r.getDeviceCount(),
                        r.getSecurityScore(), r.getQualityScore(), r.getAnomalyDetected()))
                .toList();
        List<AgentOverview.Alert> alertHistory = alerts
                .findByNetworkIdOrderByCreatedAtDesc(networkId, PageRequest.of(0, ALERTS_SHOWN)).stream()
                .map(a -> new AgentOverview.Alert(a.getType().name(), a.getDetail(), a.getCreatedAt()))
                .toList();
        return new AgentOverview(
                key == null ? null : new AgentOverview.KeyInfo(key.getKeyPrefix(), key.getCreatedAt(), key.getLastSeenAt(),
                        IdentifyMode.visible(key.getIdentifyUntil(), now)),
                online, latest == null ? null : toView(latest), history, devices(networkId, latest, now), alertHistory);
    }

    private List<AgentOverview.Device> devices(Long networkId, AgentReport latest, Instant now) {
        List<AgentDevice> all = devices.findByNetworkIdOrderByFirstSeenAtAsc(networkId);
        if (all.isEmpty()) {
            return List.of();
        }
        Instant learnedAt = all.get(0).getFirstSeenAt();  // devices of the first report are not "new"
        Instant latestAt = latest == null ? null : latest.getReceivedAt();
        // Unauthorized devices first, then by most recently seen.
        return all.stream()
                .sorted(Comparator.comparing(AgentDevice::isAuthorized)
                        .thenComparing(Comparator.comparing(AgentDevice::getLastSeenAt).reversed())
                        .thenComparing(AgentDevice::getFirstSeenAt))
                .map(d -> new AgentOverview.Device(d.getFingerprint(), d.getLabel(), d.getVendor(), d.getHostname(),
                        d.isPrivateMac(), d.isAuthorized(), d.getFirstSeenAt(), d.getLastSeenAt(),
                        latestAt != null && !d.getLastSeenAt().isBefore(latestAt),
                        d.getFirstSeenAt().isAfter(learnedAt) && d.getFirstSeenAt().isAfter(now.minus(RECENT_DEVICE)),
                        d.getKind(), d.getRole(), d.getModel()))
                .toList();
    }

    private AgentOverview.Report toView(AgentReport r) {
        return new AgentOverview.Report(r.getId(), r.getReceivedAt(), r.getAgentVersion(), r.getSsid(),
                r.getAuthentication(), r.getBand(), r.getChannel(), r.getSignalDbm(), r.getLatencyMs(), r.getJitterMs(),
                r.getPacketLossPct(), r.getGatewayLatencyMs(), r.getDeviceCount(), r.getNewDeviceCount(),
                r.getSecurityScore(), r.getSecurityGrade(), r.getQualityScore(), r.getAnomalyDetected(),
                r.getAnomalyType(), details(r.getDetails()));
    }

    private JsonNode details(String text) {
        try {
            return json.readTree(text == null ? "{}" : text);
        } catch (JsonProcessingException e) {
            return json.createObjectNode();
        }
    }
}
