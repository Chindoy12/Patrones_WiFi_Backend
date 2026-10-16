package com.wifisense.service;

import com.wifisense.model.AgentDevice;
import com.wifisense.model.HomeNetwork;
import com.wifisense.model.User;
import com.wifisense.repository.AgentDeviceRepository;
import com.wifisense.repository.HomeNetworkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentDeviceServiceTest {

    private static final String FP = "a".repeat(24);

    private final HomeNetworkRepository networks = mock(HomeNetworkRepository.class);
    private final AgentDeviceRepository devices = mock(AgentDeviceRepository.class);
    private final AgentDeviceService service = new AgentDeviceService(networks, devices);
    private final HomeNetwork casa = new HomeNetwork(mock(User.class), "Casa", null, null);
    private final AgentDevice device = new AgentDevice(7L, FP, Instant.parse("2026-10-16T12:00:00Z"));

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(casa, "id", 7L);
        device.identify("Apple", "iPhone-de-Ana");
        when(networks.findByIdAndOwner_Username(7L, "ana")).thenReturn(Optional.of(casa));
        when(devices.findByNetworkIdAndFingerprint(7L, FP)).thenReturn(Optional.of(device));
    }

    @Test
    void renamesAndBlocksTheDevice() {
        var updated = service.update("ana", 7L, FP, "  Celular de Ana  ", false);

        assertThat(updated.label()).isEqualTo("Celular de Ana");
        assertThat(updated.authorized()).isFalse();
        assertThat(updated.vendor()).isEqualTo("Apple");
        assertThat(device.isAuthorized()).isFalse();
    }

    @Test
    void anEmptyNameClearsTheLabelAndNullLeavesFieldsAsTheyAre() {
        device.rename("Viejo");
        var cleared = service.update("ana", 7L, FP, "   ", null);
        assertThat(cleared.label()).isNull();
        assertThat(cleared.authorized()).isTrue();
    }

    @Test
    void someoneElsesNetworkOrAnUnknownDeviceIsNotFound() {
        assertThatThrownBy(() -> service.update("eva", 7L, FP, "x", null))
                .isInstanceOf(ResourceNotFoundException.class);
        when(devices.findByNetworkIdAndFingerprint(7L, "z".repeat(24))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update("ana", 7L, "z".repeat(24), "x", null))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
