package com.wifisense.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifisense.dto.AgentOverview;
import com.wifisense.model.NetworkSensor;
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
    private final AgentOverviewAssembler views;
    private final Clock clock;

    public SensorOverviewService(NetworkRepository networks, NetworkSensorRepository sensors,
                                 SensorReportRepository reports, SensorDeviceRepository devices,
                                 AgentProperties properties, ObjectMapper json, Clock clock) {
        this.networks = networks;
        this.sensors = sensors;
        this.reports = reports;
        this.devices = devices;
        this.properties = properties;
        this.views = new AgentOverviewAssembler(json);
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
        return new AgentOverview(
                sensor == null ? null
                        : new AgentOverview.KeyInfo(sensor.getKeyPrefix(), sensor.getCreatedAt(), sensor.getLastSeenAt(),
                                IdentifyMode.visible(sensor.getIdentifyUntil(), now)),
                online, views.report(latest), views.history(recent),
                views.devices(devices.findByNetworkIdOrderByFirstSeenAtAsc(networkId), latest, now), List.of());
    }
}
