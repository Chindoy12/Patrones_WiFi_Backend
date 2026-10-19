package com.wifisense.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifisense.dto.AiModelOverview;
import com.wifisense.security.SecurityConfig;
import com.wifisense.security.SecurityProperties;
import com.wifisense.dto.AiTrainingStatus;
import com.wifisense.service.AiModelService;
import com.wifisense.service.AiTrainingService;
import java.time.Instant;
import java.util.List;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private AiTrainingService training;
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
        mvc.perform(get("/api/ai/training").with(as("USER"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/ai/training").with(as("USER"))).andExpect(status().isNotFound());
        verifyNoInteractions(training);
    }

    @Test
    void theTeamSeesTheTrainingsButOnlyAnAdminTrains() throws Exception {
        Instant at = Instant.parse("2026-10-19T08:00:00Z");
        AiTrainingStatus.Version version = new AiTrainingStatus.Version(3L, "real-20261019-0800", "IsolationForest",
                at, "jefe", 2016, 2, at.minusSeconds(604800), at, JSON.readTree("{\"candidate\":{\"f1\":0.91}}"),
                "PROMOTED", "Da menos falsas alarmas con estas redes.", true);
        when(training.status()).thenReturn(new AiTrainingStatus(new AiTrainingStatus.Data(2016, 2016, 0, 2,
                at.minusSeconds(604800), at), 300, true, at.plusSeconds(604800), List.of(version)));
        when(training.train("admin")).thenReturn(version);

        mvc.perform(get("/api/ai/training").with(as("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canTrain").value(true))
                .andExpect(jsonPath("$.available.samples").value(2016))
                .andExpect(jsonPath("$.versions[0].metrics.candidate.f1").value(0.91));
        mvc.perform(post("/api/ai/training").with(as("ANALYST"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/ai/training").with(jwt().jwt(token -> token.subject("admin"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("PROMOTED"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void tooFewMeasurementsIsExplained() throws Exception {
        when(training.train("someone")).thenThrow(new IllegalStateException("Se necesitan al menos 300 mediciones."));

        mvc.perform(post("/api/ai/training").with(as("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Se necesitan al menos 300 mediciones."));
    }
}
