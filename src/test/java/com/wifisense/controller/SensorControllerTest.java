package com.wifisense.controller;

import com.wifisense.dto.AgentPairingCodeResponse;
import com.wifisense.dto.SensorStatus;
import com.wifisense.security.SecurityConfig;
import com.wifisense.security.SecurityProperties;
import com.wifisense.service.NetworkSensorService;
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

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SensorController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = "wifisense.security.jwt-secret=test-secret-that-is-at-least-32-bytes-long")
class SensorControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private NetworkSensorService sensors;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(token -> token.subject(role.toLowerCase()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void theWholeTeamSeesWhetherTheSensorIsReporting() throws Exception {
        when(sensors.status(5L)).thenReturn(new SensorStatus(true, true, "wsa_abcd",
                Instant.parse("2026-10-16T10:00:00Z"), Instant.parse("2026-10-18T10:00:00Z"), "1.4.0", "Windows", null));

        mvc.perform(get("/api/networks/5/sensor").with(as("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.online").value(true))
                .andExpect(jsonPath("$.agentVersion").value("1.4.0"));
    }

    @Test
    void onlyAnAdminGeneratesTheCodeAndItIsNeverCached() throws Exception {
        when(sensors.createPairingCode(5L)).thenReturn(new AgentPairingCodeResponse("K7QM-4RZD",
                Instant.parse("2026-10-18T10:15:00Z")));

        mvc.perform(post("/api/networks/5/sensor/pairing").with(as("ANALYST"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/networks/5/sensor/pairing").with(as("ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("K7QM-4RZD"));
    }

    @Test
    void aCodeForANetworkWithoutSensorIsExplained() throws Exception {
        when(sensors.createPairingCode(6L)).thenThrow(new IllegalStateException(
                "Esta red no se mide con sensor. Edítala y elige «Sensor WiFiSense» como fuente de datos."));

        mvc.perform(post("/api/networks/6/sensor/pairing").with(as("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Esta red no se mide con sensor")));
    }

    @Test
    void onlyAnAdminRemovesTheSensor() throws Exception {
        mvc.perform(delete("/api/networks/5/sensor").with(as("VIEWER"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/networks/5/sensor").with(as("ADMIN"))).andExpect(status().isNoContent());
        verify(sensors).revoke(5L);
    }

    @Test
    void anEndUserDoesNotKnowTheSensorsExist() throws Exception {
        mvc.perform(get("/api/networks/5/sensor").with(as("USER"))).andExpect(status().isNotFound());
    }
}
