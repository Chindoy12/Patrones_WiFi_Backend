package com.wifisense.controller;

import com.wifisense.dto.AgentOverview;
import com.wifisense.dto.AgentPairingCodeResponse;
import com.wifisense.dto.DeviceUpdateRequest;
import com.wifisense.dto.SensorStatus;
import com.wifisense.service.NetworkSensorService;
import com.wifisense.service.SensorDeviceService;
import com.wifisense.service.SensorOverviewService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The sensor of a team network. Anyone of the team sees its state, its latest report and the devices it sees; only an
 * ADMIN generates the installation code, removes the sensor or names and blocks a device (POST, PUT and DELETE under
 * /api/** are ADMIN only in SecurityConfig).
 */
@RestController
@RequestMapping("/api/networks/{networkId}/sensor")
public class SensorController {

    private final NetworkSensorService sensors;
    private final SensorOverviewService overview;
    private final SensorDeviceService devices;

    public SensorController(NetworkSensorService sensors, SensorOverviewService overview, SensorDeviceService devices) {
        this.sensors = sensors;
        this.overview = overview;
        this.devices = devices;
    }

    @GetMapping
    public SensorStatus status(@PathVariable Long networkId) {
        return sensors.status(networkId);
    }

    /** Latest report with the AI verdicts (security, quality, best channel), recent history and devices. */
    @GetMapping("/overview")
    public AgentOverview overview(@PathVariable Long networkId) {
        return overview.overview(networkId);
    }

    /** Name a device (blank removes the name) or mark it as authorized or not. */
    @PutMapping("/devices/{fingerprint}")
    public AgentOverview.Device updateDevice(@PathVariable Long networkId, @PathVariable String fingerprint,
                                             @Valid @RequestBody DeviceUpdateRequest request) {
        return devices.update(networkId, fingerprint, request.label(), request.authorized());
    }

    /** The code is shown once: it must not stay in any cache. */
    @PostMapping("/pairing")
    public ResponseEntity<AgentPairingCodeResponse> pairing(@PathVariable Long networkId) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(sensors.createPairingCode(networkId));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable Long networkId) {
        sensors.revoke(networkId);
    }
}
