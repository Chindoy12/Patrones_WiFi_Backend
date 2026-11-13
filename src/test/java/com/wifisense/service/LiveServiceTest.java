package com.wifisense.service;

import com.wifisense.dto.AgentLiveAck;
import com.wifisense.dto.AgentLiveSample;
import com.wifisense.dto.AgentLiveTask;
import com.wifisense.model.AgentKey;
import com.wifisense.model.HomeNetwork;
import com.wifisense.model.Network;
import com.wifisense.model.NetworkSensor;
import com.wifisense.repository.HomeNetworkRepository;
import com.wifisense.repository.NetworkRepository;
import com.wifisense.repository.NetworkSensorRepository;
import com.wifisense.service.LiveMonitor.Kind;
import com.wifisense.service.LiveMonitor.Target;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class LiveServiceTest {

    private static final String SENSOR_KEY = "wsa_" + "s".repeat(43);
    private static final String HOME_KEY = "wsa_" + "h".repeat(43);
    private static final AgentLiveSample SAMPLE = new AgentLiveSample(2000, 1_000_000L, 1000L, 30.0, null, 3.0, null,
            70, null, null);

    private final LiveMonitor monitor = new LiveMonitor(new TestClock(Instant.parse("2026-11-11T15:00:00Z")));
    private final NetworkSensorService sensors = mock(NetworkSensorService.class);
    private final AgentKeyService keys = mock(AgentKeyService.class);
    private final HomeNetworkRepository homes = mock(HomeNetworkRepository.class);
    private final NetworkRepository networks = mock(NetworkRepository.class);
    private final NetworkSensorRepository networkSensors = mock(NetworkSensorRepository.class);
    private final LiveService service = new LiveService(monitor, sensors, keys, homes, networks, networkSensors);

    private static NetworkSensor sensorOf(long networkId) {
        Network network = mock(Network.class);
        when(network.getId()).thenReturn(networkId);
        NetworkSensor sensor = mock(NetworkSensor.class);
        when(sensor.getNetwork()).thenReturn(network);
        return sensor;
    }

    private static AgentKey keyOf(long networkId) {
        HomeNetwork network = mock(HomeNetwork.class);
        when(network.getId()).thenReturn(networkId);
        AgentKey key = mock(AgentKey.class);
        when(key.getNetwork()).thenReturn(network);
        return key;
    }

    @Test
    void theOwnerWatchesTheirNetworkAndItsAgentIsToldToMeasure() {
        when(homes.findByIdAndOwner_Username(7L, "ana")).thenReturn(Optional.of(mock(HomeNetwork.class)));
        when(sensors.authenticate(HOME_KEY)).thenReturn(Optional.empty());
        AgentKey key = keyOf(7L);
        when(keys.authenticate(HOME_KEY)).thenReturn(key);

        service.watchHome("ana", 7L, 0);

        assertThat(service.next(HOME_KEY)).contains(new AgentLiveTask(true, 2, 600));
        assertThat(service.receive(HOME_KEY, SAMPLE)).isEqualTo(new AgentLiveAck(true));
        assertThat(service.watchHome("ana", 7L, 0).points()).hasSize(1);
    }

    @Test
    void anotherUsersNetworkLooksMissing() {
        when(homes.findByIdAndOwner_Username(7L, "eva")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.watchHome("eva", 7L, 0)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(monitor.channelCount()).isZero();
    }

    @Test
    void theTeamWatchesANetworkWithItsSensorAndTheSensorKeyDecidesTheNetwork() {
        when(networks.findById(7L)).thenReturn(Optional.of(mock(Network.class)));
        NetworkSensor sensor = sensorOf(7L);
        when(networkSensors.findByNetworkId(7L)).thenReturn(Optional.of(sensor));
        when(sensors.authenticate(SENSOR_KEY)).thenReturn(Optional.of(sensor));

        service.watchTeam(7L, 0);

        assertThat(service.next(SENSOR_KEY)).isPresent();
        verifyNoInteractions(keys);  // a sensor key never reaches the keys of Mi Wi-Fi
        assertThat(monitor.agentAsks(new Target(Kind.HOME, 7L))).isEmpty();  // the home network 7 is another one
    }

    @Test
    void aTeamNetworkWithoutSensorHasNothingToWatch() {
        when(networks.findById(7L)).thenReturn(Optional.of(mock(Network.class)));
        when(networkSensors.findByNetworkId(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.watchTeam(7L, 0)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tiene un sensor");
    }

    @Test
    void aMissingTeamNetworkIsNotFound() {
        when(networks.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.watchTeam(9L, 0)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anUnknownKeyIsRejected() {
        when(sensors.authenticate("wsa_otra")).thenReturn(Optional.empty());
        when(keys.authenticate("wsa_otra")).thenThrow(new InvalidAgentKeyException());

        assertThatThrownBy(() -> service.next("wsa_otra")).isInstanceOf(InvalidAgentKeyException.class);
        assertThatThrownBy(() -> service.receive("wsa_otra", SAMPLE)).isInstanceOf(InvalidAgentKeyException.class);
    }
}
