package com.wifisense.controller;

import com.wifisense.dto.TeamNetworksOverview;
import com.wifisense.service.TeamNetworkService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Real home networks of the end users for the team panel. Only ADMIN and ANALYST (SecurityConfig). */
@RestController
@RequestMapping("/api/home-networks")
public class TeamNetworkController {

    private final TeamNetworkService service;

    public TeamNetworkController(TeamNetworkService service) {
        this.service = service;
    }

    @GetMapping
    public TeamNetworksOverview overview(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return service.overview(admin);
    }
}
