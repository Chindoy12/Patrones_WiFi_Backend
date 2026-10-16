package com.wifisense.controller;

import com.wifisense.dto.AlertPreferenceRequest;
import com.wifisense.dto.AlertPreferenceResponse;
import com.wifisense.service.AlertPreferenceService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** The end user's email-alert preferences. */
@RestController
@RequestMapping("/api/my/alerts")
public class MyAlertController {

    private final AlertPreferenceService service;

    public MyAlertController(AlertPreferenceService service) {
        this.service = service;
    }

    @GetMapping
    public AlertPreferenceResponse get(Authentication authentication) {
        return service.get(authentication.getName());
    }

    @PutMapping
    public AlertPreferenceResponse update(@Valid @RequestBody AlertPreferenceRequest request,
                                          Authentication authentication) {
        return service.update(authentication.getName(), request.newDevice(), request.security(), request.anomaly());
    }
}
