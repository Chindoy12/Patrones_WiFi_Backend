package com.wifisense.service;

import com.wifisense.dto.AgentLiveAck;
import com.wifisense.dto.AgentLiveSample;
import com.wifisense.dto.AgentLiveTask;
import com.wifisense.dto.LiveView;
import com.wifisense.model.NetworkSensor;
import com.wifisense.repository.HomeNetworkRepository;
import com.wifisense.repository.NetworkRepository;
import com.wifisense.repository.NetworkSensorRepository;
import com.wifisense.service.LiveMonitor.Kind;
import com.wifisense.service.LiveMonitor.Target;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Who may watch «En vivo» and which network an agent measures. The owner watches a network of Mi Wi-Fi (another
 * user's network is "not found"); the team watches a team network that has a sensor. The agent never says which
 * network it measures: its key decides, as with its reports (the sensor of a team network first, then the agent of
 * Mi Wi-Fi; an unknown or revoked key is 401).
 */
@Service
public class LiveService {

    private final LiveMonitor monitor;
    private final NetworkSensorService sensors;
    private final AgentKeyService keys;
    private final HomeNetworkRepository homes;
    private final NetworkRepository networks;
    private final NetworkSensorRepository networkSensors;

    public LiveService(LiveMonitor monitor, NetworkSensorService sensors, AgentKeyService keys,
                       HomeNetworkRepository homes, NetworkRepository networks, NetworkSensorRepository networkSensors) {
        this.monitor = monitor;
        this.sensors = sensors;
        this.keys = keys;
        this.homes = homes;
        this.networks = networks;
        this.networkSensors = networkSensors;
    }

    @Transactional(readOnly = true)
    public LiveView watchHome(String username, Long networkId, long after) {
        homes.findByIdAndOwner_Username(networkId, username)
                .orElseThrow(() -> new ResourceNotFoundException("la red", networkId));
        return monitor.watch(new Target(Kind.HOME, networkId), after);
    }

    @Transactional(readOnly = true)
    public LiveView watchTeam(Long networkId, long after) {
        networks.findById(networkId).orElseThrow(() -> new ResourceNotFoundException("la red", networkId));
        networkSensors.findByNetworkId(networkId).orElseThrow(() -> new IllegalStateException(
                "Esta red no tiene un sensor conectado: conéctalo para ver la medición en vivo."));
        return monitor.watch(new Target(Kind.TEAM, networkId), after);
    }

    @Transactional(readOnly = true)
    public Optional<AgentLiveTask> next(String rawKey) {
        return monitor.agentAsks(target(rawKey));
    }

    @Transactional(readOnly = true)
    public AgentLiveAck receive(String rawKey, AgentLiveSample sample) {
        return monitor.receive(target(rawKey), sample);
    }

    private Target target(String rawKey) {
        Optional<NetworkSensor> sensor = sensors.authenticate(rawKey);
        if (sensor.isPresent()) {
            return new Target(Kind.TEAM, sensor.get().getNetwork().getId());
        }
        return new Target(Kind.HOME, keys.authenticate(rawKey).getNetwork().getId());  // 401 for an unknown key
    }
}
