package com.wifisense.service;

import com.wifisense.dto.AgentReportRequest;
import com.wifisense.model.AgentCapabilities;
import com.wifisense.model.AgentKey;
import com.wifisense.model.MonitorMode;
import com.wifisense.model.NetworkSensor;
import com.wifisense.repository.AgentCapabilitiesRepository;
import com.wifisense.repository.AgentKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Keeps what the computer of each agent can do, as the agent itself reports it (2.0.0): one row per agent, replaced
 * by every report. The key decides whose row it is (the sensor of a team network or the agent of a Mi Wi-Fi network);
 * the body never says it. An agent older than 2.0.0 sends nothing and its row is left as it was.
 */
@Service
public class AgentCapabilitiesService {

    private final AgentCapabilitiesRepository capabilities;
    private final NetworkSensorService sensors;
    private final AgentKeyRepository keys;
    private final Clock clock;

    public AgentCapabilitiesService(AgentCapabilitiesRepository capabilities, NetworkSensorService sensors,
                                    AgentKeyRepository keys, Clock clock) {
        this.capabilities = capabilities;
        this.sensors = sensors;
        this.keys = keys;
        this.clock = clock;
    }

    /** Called after a report was accepted; a key that is neither a sensor nor an agent changes nothing. */
    @Transactional
    public void record(String rawKey, AgentReportRequest request) {
        if (request.capabilities() == null || !AgentKeyService.wellFormed(rawKey)) {
            return;
        }
        Optional<AgentCapabilities> row = sensors.authenticate(rawKey)
                .map(NetworkSensor::getNetwork)
                .map(network -> capabilities.findByNetworkId(network.getId())
                        .orElseGet(() -> AgentCapabilities.ofSensor(network.getId())))
                .or(() -> keys.findByKeyHash(AgentKeyService.hash(rawKey))
                        .map(AgentKey::getNetwork)
                        .map(home -> capabilities.findByHomeNetworkId(home.getId())
                                .orElseGet(() -> AgentCapabilities.ofHome(home.getId()))));
        row.ifPresent(found -> {
            found.update(snapshot(request), Instant.now(clock));
            capabilities.save(found);
        });
    }

    /** The capabilities of the sensor of a team network, if its agent ever reported them. */
    @Transactional(readOnly = true)
    public Optional<AgentCapabilities> ofSensor(Long networkId) {
        return capabilities.findByNetworkId(networkId);
    }

    static AgentCapabilities.Snapshot snapshot(AgentReportRequest request) {
        AgentReportRequest.Capabilities c = request.capabilities();
        MonitorMode monitor = c.monitorMode() == null ? MonitorMode.UNKNOWN : MonitorMode.valueOf(c.monitorMode());
        String platform = request.platform() == null || request.platform().isBlank() ? null : request.platform().strip();
        return new AgentCapabilities.Snapshot(platform, request.agentVersion(), yes(c.elevated()),
                yes(c.captureConsent()), yes(c.npcap()), yes(c.pktmon()), yes(c.rawSocket()), yes(c.packetSocket()),
                monitor, c.captureInterfaces() == null ? 0 : c.captureInterfaces());
    }

    private static boolean yes(Boolean value) {
        return Boolean.TRUE.equals(value);
    }
}
