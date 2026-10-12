package com.wifisense.controller;

import com.wifisense.dto.ConnectionTestResponse;
import com.wifisense.security.SecurityConfig;
import com.wifisense.security.SecurityProperties;
import com.wifisense.service.ConnectionTestService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MyConnectionTestController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = "wifisense.security.jwt-secret=test-secret-that-is-at-least-32-bytes-long")
class MyConnectionTestControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ConnectionTestService service;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private static RequestPostProcessor as(String username, String role) {
        return jwt().jwt(token -> token.subject(username)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void savesTheTestForTheOwnerInTheToken() throws Exception {
        when(service.save(eq("ana"), eq(7L), any())).thenReturn(new ConnectionTestResponse(1L, 7L, 24, 3, 180, 40,
                88, "EXCELLENT", null, Instant.parse("2026-10-12T12:00:00Z")));

        mvc.perform(post("/api/my/networks/7/tests").with(as("ana", "USER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latencyMs\":24,\"jitterMs\":3,\"downloadMbps\":180,\"uploadMbps\":40}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.qualityScore").value(88));
        verify(service).save(eq("ana"), eq(7L), any());
    }

    @Test
    void absurdValuesGetSpanishFieldErrors() throws Exception {
        mvc.perform(post("/api/my/networks/7/tests").with(as("ana", "USER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latencyMs\":0,\"jitterMs\":3,\"downloadMbps\":50000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.latencyMs").value("La latencia debe estar entre 0,1 y 10 000 ms."))
                .andExpect(jsonPath("$.errors.downloadMbps").value("La velocidad de bajada debe estar entre 0 y 10 000 Mbps."))
                .andExpect(jsonPath("$.errors.uploadMbps").value("Falta la velocidad de subida."));
        verifyNoInteractions(service);
    }

    @Test
    void historyIsOnlyForEndUsers() throws Exception {
        mvc.perform(get("/api/my/networks/7/tests").with(as("jefe", "ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/my/networks/7/tests").with(as("ana", "USER"))).andExpect(status().isOk());
        verify(service).history("ana", 7L, 10);
    }
}
