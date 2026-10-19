package com.wifisense.controller;

import com.wifisense.dto.AgentKeyCreated;
import com.wifisense.dto.AgentOverview;
import com.wifisense.dto.AgentPairingCodeResponse;
import com.wifisense.dto.DeviceSessionHistory;
import com.wifisense.dto.DeviceUpdateRequest;
import com.wifisense.service.AgentDeviceService;
import com.wifisense.service.AgentKeyService;
import com.wifisense.service.AgentOverviewService;
import com.wifisense.service.AgentPairingService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** The agent of one of the user's networks: what it reported, and its key. */
@RestController
@RequestMapping("/api/my/networks/{networkId}/agent")
public class MyAgentController {

    private final AgentOverviewService overview;
    private final AgentKeyService keys;
    private final AgentPairingService pairing;
    private final AgentDeviceService deviceService;

    public MyAgentController(AgentOverviewService overview, AgentKeyService keys, AgentPairingService pairing,
                            AgentDeviceService deviceService) {
        this.overview = overview;
        this.keys = keys;
        this.pairing = pairing;
        this.deviceService = deviceService;
    }

    @GetMapping
    public AgentOverview overview(@PathVariable Long networkId, Authentication authentication) {
        return overview.overview(authentication.getName(), networkId);
    }

    /** When a device was connected in the last days (default 7, at most 30). */
    @GetMapping("/devices/{fingerprint}/sessions")
    public DeviceSessionHistory sessions(@PathVariable Long networkId, @PathVariable String fingerprint,
                                         @RequestParam(defaultValue = "7") int days, Authentication authentication) {
        return deviceService.history(authentication.getName(), networkId, fingerprint, days);
    }

    /** Rename a device or mark it authorized/not authorized. */
    @PutMapping("/devices/{fingerprint}")
    public AgentOverview.Device updateDevice(@PathVariable Long networkId, @PathVariable String fingerprint,
                                             @Valid @RequestBody DeviceUpdateRequest request,
                                             Authentication authentication) {
        return deviceService.update(authentication.getName(), networkId, fingerprint, request.label(),
                request.authorized());
    }

    /** The key travels only in this response; it must not be cached anywhere. */
    @PostMapping("/key")
    public ResponseEntity<AgentKeyCreated> createKey(@PathVariable Long networkId, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(keys.create(authentication.getName(), networkId));
    }

    /** Short code to type in the installer instead of the key; also never cached. */
    @PostMapping("/pairing")
    public ResponseEntity<AgentPairingCodeResponse> createPairingCode(@PathVariable Long networkId,
                                                                      Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(pairing.create(authentication.getName(), networkId));
    }

    @DeleteMapping("/key")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeKey(@PathVariable Long networkId, Authentication authentication) {
        keys.revoke(authentication.getName(), networkId);
    }
}
