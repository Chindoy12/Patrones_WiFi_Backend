package com.wifisense.controller;

import com.wifisense.dto.AgentKeyCreated;
import com.wifisense.dto.AgentOverview;
import com.wifisense.security.SecurityConfig;
import com.wifisense.security.SecurityProperties;
import com.wifisense.service.AgentKeyService;
import com.wifisense.service.AgentDeviceService;
import com.wifisense.service.AgentOverviewService;
import com.wifisense.service.AgentPairingService;
import com.wifisense.service.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MyAgentController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = "wifisense.security.jwt-secret=test-secret-that-is-at-least-32-bytes-long")
class MyAgentControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AgentOverviewService overview;
    @MockitoBean
    private AgentKeyService keys;
    @MockitoBean
    private AgentPairingService pairing;
    @MockitoBean
    private AgentDeviceService deviceService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private static RequestPostProcessor as(String username, String role) {
        return jwt().jwt(token -> token.subject(username)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void theKeyIsShownOnceAndNeverCached() throws Exception {
        when(keys.create("ana", 7L)).thenReturn(new AgentKeyCreated("wsa_" + "x".repeat(43), "wsa_xxxx",
                Instant.parse("2026-10-12T12:00:00Z")));

        mvc.perform(post("/api/my/networks/7/agent/key").with(as("ana", "USER")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.prefix").value("wsa_xxxx"));
    }

    @Test
    void overviewOfTheOwnersNetwork() throws Exception {
        when(overview.overview("ana", 7L)).thenReturn(new AgentOverview(null, false, null, List.of(), List.of(), List.of()));

        mvc.perform(get("/api/my/networks/7/agent").with(as("ana", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.online").value(false));
    }

    @Test
    void revokingAnotherUsersKeyLooksLikeAMissingNetwork() throws Exception {
        doThrow(new ResourceNotFoundException("la red", 7L)).when(keys).revoke("eva", 7L);

        mvc.perform(delete("/api/my/networks/7/agent/key").with(as("eva", "USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void teamRolesDoNotHaveAgents() throws Exception {
        mvc.perform(get("/api/my/networks/7/agent").with(as("jefe", "ADMIN")))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(overview, keys);
    }

    @Test
    void thePairingCodeIsForTheOwnerAndNotCached() throws Exception {
        when(pairing.create("ana", 7L)).thenReturn(new com.wifisense.dto.AgentPairingCodeResponse("K7QM-4RZD",
                Instant.parse("2026-10-16T15:15:00Z")));

        mvc.perform(post("/api/my/networks/7/agent/pairing").with(as("ana", "USER")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("K7QM-4RZD"));
    }

    @Test
    void namesAndBlocksADeviceForTheOwner() throws Exception {
        when(deviceService.update(eq("ana"), eq(7L), eq("a".repeat(24)), eq("Celular de Ana"), eq(false)))
                .thenReturn(new AgentOverview.Device("a".repeat(24), "Celular de Ana", "Apple", "iPhone-de-Ana", false,
                        false, Instant.parse("2026-10-12T12:00:00Z"), Instant.parse("2026-10-12T12:00:00Z"), true,
                        false));

        mvc.perform(put("/api/my/networks/7/agent/devices/" + "a".repeat(24)).with(as("ana", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Celular de Ana\",\"authorized\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorized").value(false))
                .andExpect(jsonPath("$.vendor").value("Apple"));
    }

    @Test
    void theOwnerAsksTheAgentToReportEveryMinute() throws Exception {
        when(keys.identify("ana", 7L)).thenReturn(new com.wifisense.dto.IdentifyModeResponse(
                Instant.parse("2026-10-19T15:10:00Z"), 60));
        when(keys.identify("luis", 7L)).thenThrow(new com.wifisense.service.ResourceNotFoundException("la red", 7L));

        mvc.perform(post("/api/my/networks/7/agent/identify").with(as("ana", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportSeconds").value(60));
        mvc.perform(post("/api/my/networks/7/agent/identify").with(as("luis", "USER")))
                .andExpect(status().isNotFound());
    }
}
