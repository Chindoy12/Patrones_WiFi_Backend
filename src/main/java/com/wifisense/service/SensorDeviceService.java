package com.wifisense.service;

import com.wifisense.dto.AgentOverview;
import com.wifisense.dto.DeviceSessionHistory;
import com.wifisense.dto.AgentReportRequest;
import com.wifisense.model.SensorDevice;
import com.wifisense.repository.NetworkRepository;
import com.wifisense.repository.SensorDeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * The devices the sensor of a team network sees, as Mi Wi-Fi does for a home: each one only by a salted fingerprint
 * of its MAC, with the maker, the name it announces and whether it uses a private address. An administrator can name
 * a device and mark it as not authorized.
 */
@Service
public class SensorDeviceService {

    private final NetworkRepository networks;
    private final SensorDeviceRepository devices;
    private final DeviceSessionTracker sessions;
    private final TeamDeviceRegistry registry;

    public SensorDeviceService(NetworkRepository networks, SensorDeviceRepository devices,
                               DeviceSessionTracker sessions) {
        this.networks = networks;
        this.devices = devices;
        this.sessions = sessions;
        this.registry = new TeamDeviceRegistry(devices, sessions);
    }

    /**
     * Records every device of the report (the same steps as Mi Wi-Fi, in {@link DeviceRegistry}). The first report of
     * a network only learns its devices: they are all "new", but announcing them would be noise.
     */
    @Transactional
    public DeviceRegistry.Outcome register(Long networkId, AgentReportRequest request, Instant now) {
        return registry.register(networkId, request, now);
    }

    /** When a device of the network was connected in the last days (1 to 30). */
    @Transactional(readOnly = true)
    public DeviceSessionHistory history(Long networkId, String fingerprint, int days) {
        if (!networks.existsById(networkId)) {
            throw new ResourceNotFoundException("la red", networkId);
        }
        devices.findByNetworkIdAndFingerprint(networkId, fingerprint)
                .orElseThrow(() -> new ResourceNotFoundException("el equipo", fingerprint));
        return sessions.history(DeviceSessionTracker.Owner.TEAM, networkId, fingerprint, days);
    }

    /** ADMIN: rename a device (blank removes the name) or mark it as authorized or not. */
    @Transactional
    public AgentOverview.Device update(Long networkId, String fingerprint, String label, Boolean authorized) {
        if (!networks.existsById(networkId)) {
            throw new ResourceNotFoundException("la red", networkId);
        }
        SensorDevice device = devices.findByNetworkIdAndFingerprint(networkId, fingerprint)
                .orElseThrow(() -> new ResourceNotFoundException("el equipo", fingerprint));
        device.edit(label, authorized);
        return AgentOverview.Device.of(device, false, false);
    }
}
