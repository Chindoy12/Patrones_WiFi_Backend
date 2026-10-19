package com.wifisense.service;

import com.wifisense.dto.AgentOverview;
import com.wifisense.dto.DeviceSessionHistory;
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
    private final DeviceSessionTracker sessions;

    public AgentDeviceService(HomeNetworkRepository networks, AgentDeviceRepository devices,
                              DeviceSessionTracker sessions) {
        this.networks = networks;
        this.devices = devices;
        this.sessions = sessions;
    }

    /** When a device of one of the user's networks was connected in the last days (1 to 30). */
    @Transactional(readOnly = true)
    public DeviceSessionHistory history(String username, Long networkId, String fingerprint, int days) {
        networks.findByIdAndOwner_Username(networkId, username)
                .orElseThrow(() -> new ResourceNotFoundException("la red", networkId));
        devices.findByNetworkIdAndFingerprint(networkId, fingerprint)
                .orElseThrow(() -> new ResourceNotFoundException("el equipo", fingerprint));
        return sessions.history(DeviceSessionTracker.Owner.HOME, networkId, fingerprint, days);
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
                device.getHostname(), device.isPrivateMac(), device.isAuthorized(), device.getFirstSeenAt(),
                device.getLastSeenAt(), false, false, device.getKind(), device.getRole(), device.getModel());
    }
}
