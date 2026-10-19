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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public SensorDeviceService(NetworkRepository networks, SensorDeviceRepository devices,
                               DeviceSessionTracker sessions) {
        this.networks = networks;
        this.devices = devices;
        this.sessions = sessions;
    }

    /** How many devices of a report had never been seen, and how many marked as not authorized are connected. */
    public record Outcome(int newDevices, int unauthorizedPresent) {
    }

    /**
     * Records every device of the report. The first report of a network only learns its devices: they are all
     * "new", but announcing them would be noise.
     */
    @Transactional
    public Outcome register(Long networkId, AgentReportRequest request, Instant now) {
        List<String> hashes = request.deviceHashesOrEmpty().stream().distinct().toList();
        Map<String, String> vendors = request.vendorsByFingerprint();
        Map<String, String> names = request.namesByFingerprint();
        Set<String> privates = request.privateFingerprints();
        Map<String, AgentReportRequest.Identity> identities = request.identitiesByFingerprint();
        boolean learning = !devices.existsByNetworkId(networkId);
        Map<String, SensorDevice> known = new HashMap<>();
        devices.findByNetworkIdOrderByFirstSeenAtAsc(networkId).forEach(d -> known.put(d.getFingerprint(), d));
        int fresh = 0;
        int unauthorized = 0;
        for (String hash : hashes) {
            SensorDevice device = known.get(hash);
            if (device == null) {
                device = new SensorDevice(networkId, hash, now);
                device.identify(vendors.get(hash), names.get(hash));
                describe(device, identities.get(hash));
                device.markPrivateMac(privates.contains(hash));
                devices.save(device);
                fresh++;
            } else {
                device.seen(now);
                device.identify(vendors.get(hash), names.get(hash));
                describe(device, identities.get(hash));
                device.markPrivateMac(privates.contains(hash));
                if (!device.isAuthorized()) {
                    unauthorized++;
                }
            }
        }
        devices.flush();  // the periods below reference the devices just saved
        sessions.track(DeviceSessionTracker.Owner.TEAM, networkId, hashes, now);
        return new Outcome(learning ? 0 : fresh, unauthorized);
    }

    private static void describe(SensorDevice device, AgentReportRequest.Identity identity) {
        if (identity != null) {
            device.describe(identity.kind(), identity.role(), identity.model());
        }
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
