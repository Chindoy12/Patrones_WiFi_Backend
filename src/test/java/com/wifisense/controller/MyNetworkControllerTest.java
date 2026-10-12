package com.wifisense.controller;

import com.wifisense.dto.HomeNetworkResponse;
import com.wifisense.security.SecurityConfig;
import com.wifisense.security.SecurityProperties;
import com.wifisense.service.HomeNetworkService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MyNetworkController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = "wifisense.security.jwt-secret=test-secret-that-is-at-least-32-bytes-long")
class MyNetworkControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private HomeNetworkService service;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private static RequestPostProcessor as(String username, String role) {
        return jwt().jwt(token -> token.subject(username)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void endUsersSeeOnlyTheirOwnNetworks() throws Exception {
        when(service.list("ana")).thenReturn(List.of(new HomeNetworkResponse(1L, "Casa", "Claro", 300,
                Instant.parse("2026-10-11T12:00:00Z"))));

        mvc.perform(get("/api/my/networks").with(as("ana", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Casa"));
        verify(service).list("ana");
    }

    @Test
    void theOwnerComesFromTheTokenNotFromTheBody() throws Exception {
        when(service.create(eq("ana"), any())).thenReturn(new HomeNetworkResponse(2L, "Oficina", null, null, Instant.now()));

        mvc.perform(post("/api/my/networks").with(as("ana", "USER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Oficina\",\"owner\":\"admin\"}"))
                .andExpect(status().isCreated());
        verify(service).create(eq("ana"), any());
    }

    @Test
    void invalidNamesGetSpanishFieldErrors() throws Exception {
        mvc.perform(post("/api/my/networks").with(as("ana", "USER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"<script>\",\"contractedMbps\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("El nombre solo puede tener letras, números, espacios y . _ ' -"))
                .andExpect(jsonPath("$.errors.contractedMbps").value("La velocidad contratada debe estar entre 1 y 10 000 Mbps."));
        verifyNoInteractions(service);
    }

    @Test
    void anotherUsersNetworkAnswers404() throws Exception {
        doThrow(new ResourceNotFoundException("la red", 9L)).when(service).delete("ana", 9L);

        mvc.perform(delete("/api/my/networks/9").with(as("ana", "USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlySignedInEndUsersUseThisArea() throws Exception {
        mvc.perform(get("/api/my/networks")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/my/networks").with(as("jefe", "ADMIN"))).andExpect(status().isForbidden());
    }
}
