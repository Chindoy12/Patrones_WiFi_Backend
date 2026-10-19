package com.wifisense.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifisense.dto.AgentOverview;
import com.wifisense.model.NetworkSensor;
import com.wifisense.model.SensorDevice;
import com.wifisense.model.SensorReport;
import com.wifisense.repository.NetworkRepository;
import com.wifisense.repository.NetworkSensorRepository;
import com.wifisense.repository.SensorDeviceRepository;
import com.wifisense.repository.SensorReportRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * What the team panel shows about the sensor of a network, in the same shape Mi Wi-Fi uses for its agent, so the page
 * shows both with the same view: latest report with the AI verdicts, recent history and the devices. The alerts of a
 * team network live in the Alerts page, so none come here.
 */
@Service
public class SensorOverviewService {

    private final NetworkRepository networks;
    private final NetworkSensorRepository sensors;
    private final SensorReportRepository reports;
    private final SensorDeviceRepository devices;
    private final AgentProperties properties;
    private final ObjectMapper json;
    private final Clock clock;

    public SensorOverviewService(NetworkRepository networks, NetworkSensorRepository sensors,
                                 SensorReportRepository reports, SensorDeviceRepository devices,
                                 AgentProperties properties, ObjectMapper json, Clock clock) {
        this.networks = networks;
        this.sensors = sensors;
        this.reports = reports;
        this.devices = devices;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AgentOverview overview(Long networkId) {
        if (!networks.existsById(networkId)) {
            throw new ResourceNotFoundException("la red", networkId);
        }
        Instant now = Instant.now(clock);
        NetworkSensor sensor = sensors.findByNetworkId(networkId).orElse(null);
        List<SensorReport> recent = reports.findByNetworkIdOrderByReceivedAtDesc(networkId,
                PageRequest.of(0, Math.max(properties.historySize(), 1)));
        SensorReport latest = recent.isEmpty() ? null : recent.get(0);
        boolean online = sensor != null && sensor.getLastSeenAt() != null
                && sensor.getLastSeenAt().isAfter(now.minus(properties.onlineWindow()));
        List<AgentOverview.HistoryPoint> history = recent.stream()
                .sorted(Comparator.comparing(SensorReport::getReceivedAt))
                .map(r -> new AgentOverview.HistoryPoint(r.getReceivedAt(), r.getLatencyMs(), r.getDeviceCount(),
                        r.getSecurityScore(), r.getQualityScore(), null))
                .toList();
        return new AgentOverview(
                sensor == null ? null
                        : new AgentOverview.KeyInfo(sensor.getKeyPrefix(), sensor.getCreatedAt(), sensor.getLastSeenAt(),
                                IdentifyMode.visible(sensor.getIdentifyUntil(), now)),
                online, latest == null ? null : toView(latest), history, devices(networkId, latest, now), List.of());
    }

    /** Not authorized first, then the most recently seen; "recent" are those that joined in the last 24 hours. */
    private List<AgentOverview.Device> devices(Long networkId, SensorReport latest, Instant now) {
        List<SensorDevice> all = devices.findByNetworkIdOrderByFirstSeenAtAsc(networkId);
        if (all.isEmpty()) {
            return List.of();
        }
        Instant learnedAt = all.get(0).getFirstSeenAt();  // the devices of the first report are not "new"
        Instant latestAt = latest == null ? null : latest.getReceivedAt();
        return all.stream()
                .sorted(Comparator.comparing(SensorDevice::isAuthorized)
                        .thenComparing(Comparator.comparing(SensorDevice::getLastSeenAt).reversed())
                        .thenComparing(SensorDevice::getFirstSeenAt))
                .map(d -> new AgentOverview.Device(d.getFingerprint(), d.getLabel(), d.getVendor(), d.getHostname(),
                        d.isPrivateMac(), d.isAuthorized(), d.getFirstSeenAt(), d.getLastSeenAt(),
                        latestAt != null && !d.getLastSeenAt().isBefore(latestAt),
                        d.getFirstSeenAt().isAfter(learnedAt)
                                && d.getFirstSeenAt().isAfter(now.minus(AgentOverviewService.RECENT_DEVICE)),
                        d.getKind(), d.getRole(), d.getModel()))
                .toList();
    }

    private AgentOverview.Report toView(SensorReport r) {
        return new AgentOverview.Report(r.getId(), r.getReceivedAt(), r.getAgentVersion(), r.getSsid(),
                r.getAuthentication(), r.getBand(), r.getChannel(), r.getSignalDbm(), r.getLatencyMs(), r.getJitterMs(),
                r.getPacketLossPct(), r.getGatewayLatencyMs(), r.getDeviceCount(), r.getNewDeviceCount(),
                r.getSecurityScore(), r.getSecurityGrade(), r.getQualityScore(), null, null, details(r.getDetails()));
    }

    private JsonNode details(String text) {
        try {
            return json.readTree(text == null ? "{}" : text);
        } catch (JsonProcessingException e) {
            return json.createObjectNode();
        }
    }
}
