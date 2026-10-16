package com.wifisense.controller;

import com.wifisense.dto.AlertPreferenceResponse;
import com.wifisense.security.SecurityConfig;
import com.wifisense.security.SecurityProperties;
import com.wifisense.service.AlertPreferenceService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MyAlertController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = "wifisense.security.jwt-secret=test-secret-that-is-at-least-32-bytes-long")
class MyAlertControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AlertPreferenceService service;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private static RequestPostProcessor as(String username, String role) {
        return jwt().jwt(token -> token.subject(username)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void readsTheOwnerSwitches() throws Exception {
        when(service.get("ana")).thenReturn(new AlertPreferenceResponse(true, false, true));

        mvc.perform(get("/api/my/alerts").with(as("ana", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security").value(false));
    }

    @Test
    void updatesTheSwitches() throws Exception {
        when(service.update(eq("ana"), eq(true), eq(true), eq(false)))
                .thenReturn(new AlertPreferenceResponse(true, true, false));

        mvc.perform(put("/api/my/alerts").with(as("ana", "USER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newDevice\":true,\"security\":true,\"anomaly\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anomaly").value(false));
    }

    @Test
    void theTeamHasNoEndUserAlerts() throws Exception {
        mvc.perform(get("/api/my/alerts").with(as("jefe", "ADMIN"))).andExpect(status().is4xxClientError());
    }
}
