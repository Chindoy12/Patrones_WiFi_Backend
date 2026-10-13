package com.wifisense.controller;

import com.wifisense.dto.AgentReportAck;
import com.wifisense.dto.AgentReportRequest;
import com.wifisense.service.AgentReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Entry point of the WiFiSense agent. It has no user session: it authenticates with its own key in the X-Agent-Key
 * header, and the key decides which network the report belongs to (the body never says it).
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    public static final String KEY_HEADER = "X-Agent-Key";

    private final AgentReportService service;

    public AgentController(AgentReportService service) {
        this.service = service;
    }

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public AgentReportAck report(@RequestHeader(value = KEY_HEADER, required = false) String key,
                                 @Valid @RequestBody AgentReportRequest request) {
        return service.receive(key, request);
    }
}
