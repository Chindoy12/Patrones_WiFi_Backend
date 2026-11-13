package com.wifisense.controller;

import com.wifisense.dto.LiveView;
import com.wifisense.security.SecurityConfig;
import com.wifisense.security.SecurityProperties;
import com.wifisense.service.LiveService;
import com.wifisense.service.ResourceNotFoundException;
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

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = LiveController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = "wifisense.security.jwt-secret=test-secret-that-is-at-least-32-bytes-long")
class LiveControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");
    private static final LiveView VIEW = new LiveView("LIVE", NOW, NOW, 4,
            List.of(new LiveView.Point(4, NOW, 12.5, 1.2, 35.0, false, 3.0, false, 70, 144.4, 72.2)),
            new LiveView.Summary(30, 8.1, 0.9, 12.5, 1.2, 35.0, 4.0, 0.0, 3.0, 70,
                    List.of(new LiveView.Note("OK", "Todo bien en este momento: 35 ms hasta internet y sin pérdidas."))));

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LiveService live;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private static RequestPostProcessor as(String username, String role) {
        return jwt().jwt(token -> token.subject(username)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void theOwnerWatchesTheirNetworkAndNothingIsCached() throws Exception {
        when(live.watchHome("ana", 7L, 3)).thenReturn(VIEW);

        mvc.perform(get("/api/my/networks/7/live").param("after", "3").with(as("ana", "USER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.state").value("LIVE"))
                .andExpect(jsonPath("$.lastSeq").value(4))
                .andExpect(jsonPath("$.points[0].downloadMbps").value(12.5))
                .andExpect(jsonPath("$.summary.notes[0].level").value("OK"));
    }

    @Test
    void anotherUsersNetworkLooksMissing() throws Exception {
        when(live.watchHome("eva", 7L, 0)).thenThrow(new ResourceNotFoundException("la red", 7L));

        mvc.perform(get("/api/my/networks/7/live").with(as("eva", "USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void theTeamWatchesTheSensorOfANetworkEvenReadOnly() throws Exception {
        when(live.watchTeam(7L, 0)).thenReturn(VIEW);

        mvc.perform(get("/api/networks/7/sensor/live").with(as("lector", "VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.internetMs").value(35.0));
    }

    @Test
    void anEndUserCannotWatchATeamNetworkNorATeamMemberAHomeNetwork() throws Exception {
        // an end user must not learn that team features exist: 404, like a missing page
        mvc.perform(get("/api/networks/7/sensor/live").with(as("ana", "USER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/my/networks/7/live").with(as("jefe", "ADMIN")))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(live);
    }

    @Test
    void withoutASessionNothingIsShown() throws Exception {
        mvc.perform(get("/api/my/networks/7/live")).andExpect(status().isUnauthorized());
        verifyNoInteractions(live);
    }
}
