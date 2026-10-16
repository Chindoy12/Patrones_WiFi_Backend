package com.wifisense.service;

import com.wifisense.dto.AgentOverview;
import com.wifisense.model.AgentDevice;
import com.wifisense.repository.AgentDeviceRepository;
import com.wifisense.repository.HomeNetworkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lets the end user name a device and mark it as authorized or not on one of their networks. */
@Service
public class AgentDeviceService {

    private final HomeNetworkRepository networks;
    private final AgentDeviceRepository devices;

    public AgentDeviceService(HomeNetworkRepository networks, AgentDeviceRepository devices) {
        this.networks = networks;
        this.devices = devices;
    }

    @Transactional
    public AgentOverview.Device update(String username, Long networkId, String fingerprint, String label,
                                       Boolean authorized) {
        networks.findByIdAndOwner_Username(networkId, username)
                .orElseThrow(() -> new ResourceNotFoundException("la red", networkId));
        AgentDevice device = devices.findByNetworkIdAndFingerprint(networkId, fingerprint)
                .orElseThrow(() -> new ResourceNotFoundException("el equipo", fingerprint));
        if (label != null) {
            String trimmed = label.trim();
            device.rename(trimmed.isEmpty() ? null : trimmed);
        }
        if (authorized != null) {
            device.setAuthorized(authorized);
        }
        return new AgentOverview.Device(device.getFingerprint(), device.getLabel(), device.getVendor(),
                device.getHostname(), device.isAuthorized(), device.getFirstSeenAt(), device.getLastSeenAt(),
                false, false);
    }
}
