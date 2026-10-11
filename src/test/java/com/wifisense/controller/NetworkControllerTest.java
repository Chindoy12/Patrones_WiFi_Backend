package com.wifisense.controller;

import com.wifisense.analysis.AnalysisType;
import com.wifisense.analysis.NetworkAnalysisFacade;
import com.wifisense.dto.NetworkResponse;
import com.wifisense.model.Network;
import com.wifisense.model.NetworkStatus;
import com.wifisense.network.DataSourceType;
import com.wifisense.security.SecurityConfig;
import com.wifisense.security.SecurityProperties;
import com.wifisense.service.AnalysisHistoryService;
import com.wifisense.service.NetworkService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {NetworkController.class, AnalysisController.class})
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = "wifisense.security.jwt-secret=test-secret-that-is-at-least-32-bytes-long")
class NetworkControllerTest {

    private static final String VALID_NETWORK = """
            {"zoneId":1,"ssid":"Lab","bssid":"A4:2B:B0:10:00:09","frequencyBand":"5GHz","channel":36,
             "securityType":"WPA3","dataSourceType":"SIMULATION"}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private NetworkService networkService;
    @MockitoBean
    private NetworkAnalysisFacade analysisFacade;
    @MockitoBean
    private AnalysisHistoryService historyService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(token -> token.subject(role.toLowerCase()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/networks")).andExpect(status().isUnauthorized());
    }

    @Test
    void viewerCanListNetworks() throws Exception {
        when(networkService.list(null)).thenReturn(List.of(new NetworkResponse(1L, "Lab", "A4:2B:B0:10:00:09",
                "5GHz", 36, Network.SecurityType.WPA3, NetworkStatus.NORMAL, DataSourceType.SIMULATION, 1L,
                "Library", "Main Campus", null)));

        mvc.perform(get("/api/networks").with(as("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ssid").value("Lab"));
    }

    @Test
    void viewerCannotCreateNetworks() throws Exception {
        mvc.perform(post("/api/networks").with(as("VIEWER"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_NETWORK))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminGetsFieldErrorsForInvalidNetwork() throws Exception {
        mvc.perform(post("/api/networks").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_NETWORK.replace("A4:2B:B0:10:00:09", "not-a-mac")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.bssid").exists());
    }

    @Test
    void unknownNetworkReturns404() throws Exception {
        when(networkService.get(99L)).thenThrow(new ResourceNotFoundException("la red", 99L));

        mvc.perform(get("/api/networks/99").with(as("VIEWER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No se encontró la red con id 99"));
    }

    @Test
    void analystCanRunAnalysisButViewerCannot() throws Exception {
        String body = "{\"type\":\"THRESHOLD\"}";

        mvc.perform(post("/api/networks/1/analyses").with(as("VIEWER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/networks/1/analyses").with(as("ANALYST"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        verify(analysisFacade).analyze(eq(1L), eq(AnalysisType.THRESHOLD), any());
    }

    @Test
    void endUsersCannotEvenSeeThatTeamEndpointsExist() throws Exception {
        mvc.perform(get("/api/networks").with(as("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El recurso solicitado no existe."))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.instance").value("/api/networks"));
        mvc.perform(post("/api/networks/1/analyses").with(as("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"THRESHOLD\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void teamMembersStillGetAnHonestForbidden() throws Exception {
        mvc.perform(post("/api/networks/1/analyses").with(as("VIEWER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"THRESHOLD\"}"))
                .andExpect(status().isForbidden());
    }
}
