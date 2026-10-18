package com.wifisense.service;

import com.wifisense.TestData;
import com.wifisense.dto.AgentPairResponse;
import com.wifisense.dto.AgentPairingCodeResponse;
import com.wifisense.dto.SensorStatus;
import com.wifisense.model.Network;
import com.wifisense.model.NetworkSensor;
import com.wifisense.model.NetworkSensorPairingCode;
import com.wifisense.model.NetworkStatus;
import com.wifisense.network.DataSourceType;
import com.wifisense.repository.NetworkRepository;
import com.wifisense.repository.NetworkSensorPairingCodeRepository;
import com.wifisense.repository.NetworkSensorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NetworkSensorServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");

    private final NetworkRepository networks = mock(NetworkRepository.class);
    private final NetworkSensorRepository sensors = mock(NetworkSensorRepository.class);
    private final NetworkSensorPairingCodeRepository codes = mock(NetworkSensorPairingCodeRepository.class);
    private final AgentReportThrottle throttle = mock(AgentReportThrottle.class);
    private final NetworkSensorService service = new NetworkSensorService(networks, sensors, codes, throttle,
            new AgentProperties(Duration.ofSeconds(50), Duration.ofDays(30), Duration.ofMinutes(15), 20, 48),
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final Network library = TestData.network(5L, NetworkStatus.NORMAL);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(library, "dataSourceType", DataSourceType.AGENT);
        when(networks.findById(5L)).thenReturn(Optional.of(library));
        when(networks.existsById(5L)).thenReturn(true);
        when(networks.getReferenceById(5L)).thenReturn(library);
        when(sensors.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void aCodeIsReadableAndOnlyItsHashIsStored() {
        AgentPairingCodeResponse created = service.createPairingCode(5L);

        assertThat(created.code()).matches("^[A-HJKMNP-Z2-9]{4}-[A-HJKMNP-Z2-9]{4}$");
        assertThat(created.expiresAt()).isEqualTo(NOW.plus(AgentPairingService.TTL));
        ArgumentCaptor<NetworkSensorPairingCode> stored = ArgumentCaptor.forClass(NetworkSensorPairingCode.class);
        verify(codes).save(stored.capture());
        assertThat(stored.getValue().getCodeHash())
                .isEqualTo(AgentPairingService.hash(created.code().replace("-", "")));
        verify(codes).deleteByNetworkId(5L);  // a previous pending code stops working
    }

    @Test
    void onlyANetworkMeasuredBySensorGetsACode() {
        ReflectionTestUtils.setField(library, "dataSourceType", DataSourceType.SIMULATION);

        assertThatThrownBy(() -> service.createPairingCode(5L)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Sensor WiFiSense");
        assertThatThrownBy(() -> service.createPairingCode(99L)).isInstanceOf(ResourceNotFoundException.class);
        verify(codes, never()).save(any());
    }

    @Test
    void aValidCodeBecomesTheSensorKeyAndReplacesTheOldSensor() {
        NetworkSensor old = new NetworkSensor(library, "a".repeat(64), "wsa_old1");
        ReflectionTestUtils.setField(old, "id", 3L);
        when(sensors.findByNetworkId(5L)).thenReturn(Optional.of(old));
        NetworkSensorPairingCode pending = new NetworkSensorPairingCode(library, AgentPairingService.hash("K7QM4RZD"),
                NOW.minusSeconds(60), NOW.plusSeconds(840));
        when(codes.findByCodeHash(AgentPairingService.hash("K7QM4RZD"))).thenReturn(Optional.of(pending));

        AgentPairResponse paired = service.pair("K7QM4RZD").orElseThrow();

        assertThat(paired.key()).matches("^wsa_[A-Za-z0-9_-]{43}$");
        assertThat(paired.prefix()).isEqualTo(paired.key().substring(0, 8));
        assertThat(paired.networkName()).isEqualTo("WiFiSense-Test");
        ArgumentCaptor<NetworkSensor> saved = ArgumentCaptor.forClass(NetworkSensor.class);
        verify(sensors).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getKeyHash()).isEqualTo(AgentKeyService.hash(paired.key()));
        verify(codes).delete(pending);
        verify(sensors).deleteByNetworkId(5L);
        verify(throttle).forgetSensor(3L);
    }

    @Test
    void aCodeOfMiWiFiIsNotASensorCodeAndAnExpiredOneFails() {
        assertThat(service.pair("ZZZZZZZZ")).isEmpty();

        NetworkSensorPairingCode expired = new NetworkSensorPairingCode(library, AgentPairingService.hash("AAAABBBB"),
                NOW.minusSeconds(900), NOW);
        when(codes.findByCodeHash(AgentPairingService.hash("AAAABBBB"))).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.pair("AAAABBBB")).isInstanceOf(InvalidPairingCodeException.class);
        verify(sensors, never()).saveAndFlush(any());
    }

    @Test
    void theStatusSaysWhetherTheSensorIsReportingAndIfACodeIsPending() {
        NetworkSensor sensor = new NetworkSensor(library, "a".repeat(64), "wsa_abcd");
        ReflectionTestUtils.setField(sensor, "createdAt", NOW.minus(Duration.ofDays(2)));
        ReflectionTestUtils.setField(sensor, "lastSeenAt", NOW.minus(Duration.ofMinutes(4)));
        ReflectionTestUtils.setField(sensor, "agentVersion", "1.4.0");
        ReflectionTestUtils.setField(sensor, "platform", "Windows");
        when(sensors.findByNetworkId(5L)).thenReturn(Optional.of(sensor));

        SensorStatus online = service.status(5L);
        assertThat(online.installed()).isTrue();
        assertThat(online.online()).isTrue();
        assertThat(online.agentVersion()).isEqualTo("1.4.0");
        assertThat(online.pendingCodeExpiresAt()).isNull();

        ReflectionTestUtils.setField(sensor, "lastSeenAt", NOW.minus(Duration.ofMinutes(40)));
        assertThat(service.status(5L).online()).isFalse();

        when(sensors.findByNetworkId(5L)).thenReturn(Optional.empty());
        when(codes.findByNetworkId(5L)).thenReturn(Optional.of(new NetworkSensorPairingCode(library, "b".repeat(64),
                NOW.minusSeconds(60), NOW.plusSeconds(600))));
        SensorStatus waiting = service.status(5L);
        assertThat(waiting.installed()).isFalse();
        assertThat(waiting.pendingCodeExpiresAt()).isEqualTo(NOW.plusSeconds(600));
    }

    @Test
    void malformedKeysAreNotLookedUpAndRevokingRemovesSensorAndCode() {
        assertThat(service.authenticate("not-a-key")).isEmpty();
        assertThat(service.authenticate(null)).isEmpty();
        verifyNoInteractions(sensors);

        service.revoke(5L);
        verify(sensors).deleteByNetworkId(5L);
        verify(codes).deleteByNetworkId(5L);
        assertThatThrownBy(() -> service.revoke(99L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
