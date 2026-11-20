package com.wifisense.service;

import com.wifisense.dto.AgentReportRequest;
import com.wifisense.model.AgentDevice;
import com.wifisense.model.SensorDevice;
import com.wifisense.repository.AgentDeviceRepository;
import com.wifisense.repository.SensorDeviceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Template Method: the same steps record the devices of a home (Mi Wi-Fi) and of a team network (sensor); only where
 * the devices live changes.
 */
class DeviceRegistryTest {

    private static final Instant NOW = Instant.parse("2026-10-23T15:00:00Z");
    private static final String PHONE = "a".repeat(24);
    private static final String TV = "b".repeat(24);

    private static AgentReportRequest report(AgentReportRequest.DeviceInfo... devices) {
        return new AgentReportRequest("1.7.0", "Windows", "Casa", "WPA2", "CCMP", "5", 44, -58, 84, null, null, null,
                null, null, 20.0, 2.0, 0.0, null, devices.length, null, List.of(devices), List.of(), null);
    }

    /** A registry that keeps its devices in a list, to see what the template asks of the steps. */
    private static final class InMemory extends DeviceRegistry<SensorDevice> {
        final List<SensorDevice> stored = new ArrayList<>();
        int flushes;

        InMemory(DeviceSessionTracker sessions) {
            super(sessions, DeviceSessionTracker.Owner.TEAM);
        }

        @Override protected boolean hasDevices(Long networkId) { return !stored.isEmpty(); }
        @Override protected List<SensorDevice> knownDevices(Long networkId) { return List.copyOf(stored); }
        @Override protected SensorDevice newDevice(Long networkId, String fingerprint, Instant seenAt) {
            return new SensorDevice(networkId, fingerprint, seenAt);
        }
        @Override protected void save(SensorDevice device) { stored.add(device); }
        @Override protected void flush() { flushes++; }
    }

    @Test
    void theFirstReportOnlyLearnsTheDevicesAndTheNextOneCountsTheNewOnes() {
        InMemory registry = new InMemory(mock(DeviceSessionTracker.class));

        AgentReportRequest.DeviceInfo phone = new AgentReportRequest.DeviceInfo(PHONE, null, null, true);
        AgentReportRequest.DeviceInfo tvInfo =
                new AgentReportRequest.DeviceInfo(TV, "Google", "Sala TV", false, "Chromecast", "TV", null);

        DeviceRegistry.Outcome first = registry.register(5L, report(phone), NOW);
        DeviceRegistry.Outcome second = registry.register(5L, report(phone, tvInfo), NOW.plusSeconds(300));

        assertThat(first.newDevices()).isZero();
        assertThat(second.newDevices()).isEqualTo(1);
        SensorDevice tv = registry.stored.stream().filter(d -> d.getFingerprint().equals(TV)).findFirst().orElseThrow();
        assertThat(tv.getHostname()).isEqualTo("Sala TV");
        assertThat(tv.getKind()).isEqualTo("TV");
        assertThat(tv.getModel()).isEqualTo("Chromecast");
        assertThat(registry.stored.get(0).isPrivateMac()).isTrue();
        assertThat(registry.stored.get(0).getLastSeenAt()).isEqualTo(NOW.plusSeconds(300));
    }

    @Test
    void aBlockedDeviceThatIsStillConnectedIsCountedAndTheDevicesAreSavedBeforeTheirPeriods() {
        DeviceSessionTracker sessions = mock(DeviceSessionTracker.class);
        InMemory registry = new InMemory(sessions);
        AgentReportRequest.DeviceInfo phone = new AgentReportRequest.DeviceInfo(PHONE, null, null, null);
        registry.register(5L, report(phone), NOW);
        registry.stored.get(0).authorize(false);

        DeviceRegistry.Outcome outcome = registry.register(5L, report(phone), NOW);

        assertThat(outcome.unauthorizedPresent()).isEqualTo(1);
        assertThat(registry.flushes).isEqualTo(2);
        verify(sessions, times(2)).track(DeviceSessionTracker.Owner.TEAM, 5L, List.of(PHONE), NOW);
    }

    @Test
    void homesAndTeamNetworksUseTheSameStepsWithTheirOwnTables() {
        AgentDeviceRepository homes = mock(AgentDeviceRepository.class);
        SensorDeviceRepository team = mock(SensorDeviceRepository.class);
        DeviceSessionTracker sessions = mock(DeviceSessionTracker.class);
        when(homes.existsByNetworkId(7L)).thenReturn(true);
        when(team.existsByNetworkId(5L)).thenReturn(true);
        AgentReportRequest request = report(new AgentReportRequest.DeviceInfo(PHONE, "Apple", "iPhone de Ana", null));

        assertThat(new HomeDeviceRegistry(homes, sessions).register(7L, request, NOW).newDevices()).isEqualTo(1);
        assertThat(new TeamDeviceRegistry(team, sessions).register(5L, request, NOW).newDevices()).isEqualTo(1);

        InOrder home = inOrder(homes, sessions);
        home.verify(homes).save(any(AgentDevice.class));
        home.verify(homes).flush();
        home.verify(sessions).track(eq(DeviceSessionTracker.Owner.HOME), eq(7L), eq(List.of(PHONE)), eq(NOW));
        verify(team).save(any(SensorDevice.class));
        verify(sessions).track(eq(DeviceSessionTracker.Owner.TEAM), eq(5L), eq(List.of(PHONE)), eq(NOW));
    }
}
