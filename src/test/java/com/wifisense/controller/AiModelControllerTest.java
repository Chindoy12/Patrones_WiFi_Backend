package com.wifisense.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifisense.dto.AiModelOverview;
import com.wifisense.security.SecurityConfig;
import com.wifisense.security.SecurityProperties;
import com.wifisense.service.AiModelService;
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

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AiModelController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = "wifisense.security.jwt-secret=test-secret-that-is-at-least-32-bytes-long")
class AiModelControllerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AiModelService service;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(token -> token.subject("someone")).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void everyTeamRoleSeesTheModelAndItsEvaluation() throws Exception {
        when(service.overview()).thenReturn(new AiModelOverview(
                JSON.readTree("{\"algorithm\":\"IsolationForest\"}"),
                JSON.readTree("{\"productionAlgorithm\":\"IsolationForest\"}"), true));

        for (String role : new String[]{"ADMIN", "ANALYST", "VIEWER"}) {
            mvc.perform(get("/api/ai/model").with(as(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.model.algorithm").value("IsolationForest"))
                    .andExpect(jsonPath("$.evaluationReady").value(true));
        }
    }

    @Test
    void endUsersDoNotLearnTheRouteExists() throws Exception {
        mvc.perform(get("/api/ai/model").with(as("USER"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/ai/model")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
