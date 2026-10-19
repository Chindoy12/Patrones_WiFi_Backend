package com.wifisense.service;

import com.wifisense.dto.AgentOverview;
import com.wifisense.dto.AgentReportRequest;
import com.wifisense.dto.DeviceSessionHistory;
import com.wifisense.model.SensorDevice;
import com.wifisense.repository.NetworkRepository;
import com.wifisense.repository.SensorDeviceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SensorDeviceServiceTest {

    private static final Instant BEFORE = Instant.parse("2026-10-18T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");
    private static final String PHONE = "aaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String LAPTOP = "bbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String STRANGER = "cccccccccccccccccccccccc";

    private final NetworkRepository networks = mock(NetworkRepository.class);
    private final SensorDeviceRepository devices = mock(SensorDeviceRepository.class);
    private final DeviceSessionTracker sessions = mock(DeviceSessionTracker.class);
    private final SensorDeviceService service = new SensorDeviceService(networks, devices, sessions);

    private static AgentReportRequest report(String... hashes) {
        return new AgentReportRequest("1.5.0", "Windows", "Lab", "WPA2", null, "5", 36, -60, null, null, null, null,
                null, null, 20.0, 2.0, 0.0, null, hashes.length, List.of(hashes),
                List.of(new AgentReportRequest.DeviceInfo(PHONE, null, null, true),
                        new AgentReportRequest.DeviceInfo(LAPTOP, "Lenovo", "PC-Lab", false)), null, null);
    }

    @Test
    void theFirstReportOnlyLearnsTheDevicesOfTheNetwork() {
        when(devices.existsByNetworkId(5L)).thenReturn(false);
        when(devices.findByNetworkIdOrderByFirstSeenAtAsc(5L)).thenReturn(List.of());

        SensorDeviceService.Outcome outcome = service.register(5L, report(PHONE, LAPTOP), NOW);

        assertThat(outcome.newDevices()).isZero();
        ArgumentCaptor<SensorDevice> saved = ArgumentCaptor.forClass(SensorDevice.class);
        verify(devices, times(2)).save(saved.capture());
        SensorDevice laptop = saved.getAllValues().stream().filter(d -> d.getFingerprint().equals(LAPTOP))
                .findFirst().orElseThrow();
        assertThat(laptop.getVendor()).isEqualTo("Lenovo");
        assertThat(laptop.getHostname()).isEqualTo("PC-Lab");
        assertThat(saved.getAllValues().stream().filter(SensorDevice::isPrivateMac).map(SensorDevice::getFingerprint))
                .containsExactly(PHONE);
        // when each one connected: after the devices are written, since the periods reference them
        var order = inOrder(devices, sessions);
        order.verify(devices).flush();
        order.verify(sessions).track(DeviceSessionTracker.Owner.TEAM, 5L, List.of(PHONE, LAPTOP), NOW);
    }

    @Test
    void theHistoryOfADeviceIsOnlyForAKnownDeviceOfTheNetwork() {
        when(networks.existsById(5L)).thenReturn(true);
        when(devices.findByNetworkIdAndFingerprint(5L, LAPTOP)).thenReturn(Optional.of(new SensorDevice(5L, LAPTOP,
                BEFORE)));
        DeviceSessionHistory history = new DeviceSessionHistory(LAPTOP, 7, 0, 0, List.of());
        when(sessions.history(DeviceSessionTracker.Owner.TEAM, 5L, LAPTOP, 7)).thenReturn(history);

        assertThat(service.history(5L, LAPTOP, 7)).isSameAs(history);
        assertThatThrownBy(() -> service.history(5L, STRANGER, 7)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void laterADeviceNeverSeenIsNewAndABlockedOneThatIsConnectedIsCounted() {
        SensorDevice laptop = new SensorDevice(5L, LAPTOP, BEFORE);
        laptop.setAuthorized(false);
        when(devices.existsByNetworkId(5L)).thenReturn(true);
        when(devices.findByNetworkIdOrderByFirstSeenAtAsc(5L)).thenReturn(List.of(laptop));

        SensorDeviceService.Outcome outcome = service.register(5L, report(LAPTOP, STRANGER, STRANGER), NOW);

        assertThat(outcome.newDevices()).isEqualTo(1);
        assertThat(outcome.unauthorizedPresent()).isEqualTo(1);
        assertThat(laptop.getLastSeenAt()).isEqualTo(NOW);
        verify(devices, times(1)).save(any());
    }

    @Test
    void anAdminNamesADeviceAndMarksItAsNotAuthorized() {
        SensorDevice laptop = new SensorDevice(5L, LAPTOP, BEFORE);
        when(networks.existsById(5L)).thenReturn(true);
        when(devices.findByNetworkIdAndFingerprint(5L, LAPTOP)).thenReturn(Optional.of(laptop));

        AgentOverview.Device view = service.update(5L, LAPTOP, "  Portátil del laboratorio ", false);

        assertThat(view.label()).isEqualTo("Portátil del laboratorio");
        assertThat(view.authorized()).isFalse();
        service.update(5L, LAPTOP, " ", null);
        assertThat(laptop.getLabel()).isNull();
        assertThat(laptop.isAuthorized()).isFalse();
    }

    @Test
    void anUnknownNetworkOrDeviceIsNotFound() {
        when(networks.existsById(5L)).thenReturn(true);
        assertThatThrownBy(() -> service.update(9L, LAPTOP, "x", null)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.update(5L, STRANGER, "x", null)).isInstanceOf(ResourceNotFoundException.class);
    }
}
