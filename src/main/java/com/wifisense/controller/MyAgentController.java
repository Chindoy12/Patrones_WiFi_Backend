package com.wifisense.controller;

import com.wifisense.dto.AgentKeyCreated;
import com.wifisense.dto.AgentOverview;
import com.wifisense.service.AgentKeyService;
import com.wifisense.service.AgentOverviewService;
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

    public MyAgentController(AgentOverviewService overview, AgentKeyService keys) {
        this.overview = overview;
        this.keys = keys;
    }

    @GetMapping
    public AgentOverview overview(@PathVariable Long networkId, Authentication authentication) {
        return overview.overview(authentication.getName(), networkId);
    }

    /** The key travels only in this response; it must not be cached anywhere. */
    @PostMapping("/key")
    public ResponseEntity<AgentKeyCreated> createKey(@PathVariable Long networkId, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(keys.create(authentication.getName(), networkId));
    }

    @DeleteMapping("/key")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeKey(@PathVariable Long networkId, Authentication authentication) {
        keys.revoke(authentication.getName(), networkId);
    }
}
