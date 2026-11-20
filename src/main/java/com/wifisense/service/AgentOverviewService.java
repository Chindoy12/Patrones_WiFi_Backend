package com.wifisense.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifisense.dto.AgentOverview;
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
import java.time.Instant;
import java.util.List;

/** What Mi Wi-Fi shows about the agent of one network: key, latest report, history and devices. */
@Service
public class AgentOverviewService {

    static final int ALERTS_SHOWN = 10;

    private final HomeNetworkRepository networks;
    private final AgentKeyRepository keys;
    private final AgentReportRepository reports;
    private final AgentDeviceRepository devices;
    private final AgentAlertRepository alerts;
    private final AgentProperties properties;
    private final AgentOverviewAssembler views;
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
        this.views = new AgentOverviewAssembler(json);
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
        List<AgentOverview.Alert> alertHistory = alerts
                .findByNetworkIdOrderByCreatedAtDesc(networkId, PageRequest.of(0, ALERTS_SHOWN)).stream()
                .map(a -> new AgentOverview.Alert(a.getType().name(), a.getDetail(), a.getCreatedAt()))
                .toList();
        return new AgentOverview(
                key == null ? null : new AgentOverview.KeyInfo(key.getKeyPrefix(), key.getCreatedAt(), key.getLastSeenAt(),
                        IdentifyMode.visible(key.getIdentifyUntil(), now)),
                online, views.report(latest), views.history(recent),
                views.devices(devices.findByNetworkIdOrderByFirstSeenAtAsc(networkId), latest, now), alertHistory);
    }
}
