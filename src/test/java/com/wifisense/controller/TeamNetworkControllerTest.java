package com.wifisense.controller;

import com.wifisense.dto.TeamNetworksOverview;
import com.wifisense.security.SecurityConfig;
import com.wifisense.security.SecurityProperties;
import com.wifisense.service.TeamNetworkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TeamNetworkController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = "wifisense.security.jwt-secret=test-secret-that-is-at-least-32-bytes-long")
class TeamNetworkControllerTest {

    private static final TeamNetworksOverview EMPTY = new TeamNetworksOverview(
            new TeamNetworksOverview.Totals(0, 0, 0, 0, 0, 0, 0), List.of(), Instant.parse("2026-10-17T15:00:00Z"));

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TeamNetworkService service;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(token -> token.subject("equipo")).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void anAdministratorSeesTheOwners() throws Exception {
        when(service.overview(true)).thenReturn(EMPTY);

        mvc.perform(get("/api/home-networks").with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.networks").value(0));
        verify(service).overview(true);
    }

    @Test
    void anAnalystGetsTheOwnersMasked() throws Exception {
        when(service.overview(false)).thenReturn(EMPTY);

        mvc.perform(get("/api/home-networks").with(as("ANALYST"))).andExpect(status().isOk());
        verify(service).overview(false);
    }

    @Test
    void aViewerOrAnEndUserCannotSeeOtherPeoplesNetworks() throws Exception {
        mvc.perform(get("/api/home-networks").with(as("VIEWER"))).andExpect(status().isForbidden());
        // an end user does not even learn that the team endpoint exists (same as the rest of the team API)
        mvc.perform(get("/api/home-networks").with(as("USER"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/home-networks")).andExpect(status().isUnauthorized());
    }
}
