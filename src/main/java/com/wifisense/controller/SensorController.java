package com.wifisense.controller;

import com.wifisense.dto.AgentPairingCodeResponse;
import com.wifisense.dto.SensorStatus;
import com.wifisense.service.NetworkSensorService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The sensor of a team network. Anyone of the team sees its state; only an ADMIN generates the installation code or
 * removes the sensor (POST and DELETE under /api/** are ADMIN only in SecurityConfig).
 */
@RestController
@RequestMapping("/api/networks/{networkId}/sensor")
public class SensorController {

    private final NetworkSensorService sensors;

    public SensorController(NetworkSensorService sensors) {
        this.sensors = sensors;
    }

    @GetMapping
    public SensorStatus status(@PathVariable Long networkId) {
        return sensors.status(networkId);
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
