package com.wifisense.service;

import com.wifisense.dto.DeviceRequest;
import com.wifisense.dto.DeviceResponse;
import com.wifisense.dto.TrafficSessionResponse;
import com.wifisense.model.Device;
import com.wifisense.model.Network;
import com.wifisense.repository.DeviceRepository;
import com.wifisense.repository.NetworkRepository;
import com.wifisense.repository.TrafficSessionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class DeviceService {

    /** Lists never return everything: a busy network accumulates devices and sessions without end. */
    static final int MAX_DEVICES = 500;
    static final int MAX_SESSIONS = 500;

    private final DeviceRepository devices;
    private final NetworkRepository networks;
    private final TrafficSessionRepository sessions;

    public DeviceService(DeviceRepository devices, NetworkRepository networks, TrafficSessionRepository sessions) {
        this.devices = devices;
        this.networks = networks;
        this.sessions = sessions;
    }

    /** The most recently seen first. */
    public List<DeviceResponse> list(Long networkId, int limit) {
        PageRequest page = PageRequest.of(0, clamp(limit, MAX_DEVICES));
        List<Device> result = networkId == null
                ? devices.findAllByOrderByLastSeenAtDesc(page)
                : devices.findByNetworkIdOrderByLastSeenAtDesc(networkId, page);
        return result.stream().map(DeviceResponse::from).toList();
    }

    @Transactional
    public DeviceResponse create(DeviceRequest request) {
        if (devices.existsByMacAddress(request.macAddress().toUpperCase())) {
            throw new DuplicateResourceException("Ya existe un dispositivo con la MAC " + request.macAddress());
        }
        Network network = networks.findById(request.networkId())
                .orElseThrow(() -> new ResourceNotFoundException("la red", request.networkId()));
        Device device = new Device(network, request.hostname(), request.ipAddress(), request.macAddress(),
                request.type(), request.signalStrength());
        return DeviceResponse.from(devices.save(device));
    }

    /** The most recent first. */
    public List<TrafficSessionResponse> sessions(Long deviceId, int limit) {
        if (!devices.existsById(deviceId)) {
            throw new ResourceNotFoundException("el dispositivo", deviceId);
        }
        return sessions.findByDeviceIdOrderByStartedAtDesc(deviceId, PageRequest.of(0, clamp(limit, MAX_SESSIONS)))
                .stream().map(TrafficSessionResponse::from).toList();
    }

    private static int clamp(int limit, int max) {
        return Math.max(1, Math.min(limit, max));
    }
}
