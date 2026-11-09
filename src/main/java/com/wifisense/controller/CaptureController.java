package com.wifisense.controller;

import com.wifisense.dto.CaptureCapabilitiesResponse;
import com.wifisense.dto.CaptureDetail;
import com.wifisense.dto.CaptureRequest;
import com.wifisense.dto.CaptureResponse;
import com.wifisense.service.TrafficCaptureService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Technical captures of a team network, run by its sensor. Only an ADMIN can use any of these endpoints, reading
 * included (SecurityConfig); an analyst or a viewer gets 403 and the attempt is audited.
 */
@RestController
@RequestMapping("/api/networks/{networkId}/sensor/captures")
public class CaptureController {

    private final TrafficCaptureService captures;

    public CaptureController(TrafficCaptureService captures) {
        this.captures = captures;
    }

    /** What the sensor can capture now, the four scenarios and every reason that blocks a capture. */
    @GetMapping("/capabilities")
    public CaptureCapabilitiesResponse capabilities(@PathVariable Long networkId) {
        return captures.capabilities(networkId);
    }

    /** Asks the sensor for a capture with a purpose and limits; it waits for the sensor (202). */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CaptureResponse request(@PathVariable Long networkId, @Valid @RequestBody CaptureRequest request,
                                   Authentication authentication) {
        return captures.request(authentication.getName(), networkId, request);
    }

    /** History, newest first (default 20, at most 50). */
    @GetMapping
    public List<CaptureResponse> history(@PathVariable Long networkId, @RequestParam(defaultValue = "20") int limit) {
        return captures.history(networkId, limit);
    }

    /** One capture with its statistics and its findings (facts and inferences apart). */
    @GetMapping("/{captureId}")
    public CaptureDetail detail(@PathVariable Long networkId, @PathVariable Long captureId) {
        return captures.detail(networkId, captureId);
    }

    /** Stops a capture (a running one is stopped by the sensor on its next check and keeps what it captured). */
    @PostMapping("/{captureId}/cancel")
    public CaptureResponse cancel(@PathVariable Long networkId, @PathVariable Long captureId) {
        return captures.cancel(networkId, captureId);
    }
}
