package com.wifisense.controller;

import com.wifisense.dto.AgentPairRequest;
import com.wifisense.dto.AgentPairResponse;
import com.wifisense.dto.AgentReportAck;
import com.wifisense.dto.AgentReportRequest;
import com.wifisense.service.AgentPairingService;
import com.wifisense.service.AgentReportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
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
    private final AgentPairingService pairing;

    public AgentController(AgentReportService service, AgentPairingService pairing) {
        this.service = service;
        this.pairing = pairing;
    }

    /** The installer exchanges the short code from Mi Wi-Fi for the agent key (the only time the key travels). */
    @PostMapping("/pair")
    public ResponseEntity<AgentPairResponse> pair(@RequestBody AgentPairRequest request, HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        String address = StringUtils.hasText(forwarded) ? forwarded.split(",")[0].trim() : http.getRemoteAddr();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(pairing.pair(request.code(), address));
    }

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public AgentReportAck report(@RequestHeader(value = KEY_HEADER, required = false) String key,
                                 @Valid @RequestBody AgentReportRequest request) {
        return service.receive(key, request);
    }
}
