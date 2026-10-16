package com.wifisense.controller;

import com.wifisense.dto.AgentReportAck;
import com.wifisense.security.SecurityConfig;
import com.wifisense.security.SecurityProperties;
import com.wifisense.service.AgentPairingService;
import com.wifisense.service.AgentReportLimitException;
import com.wifisense.service.AgentReportService;
import com.wifisense.service.InvalidAgentKeyException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AgentController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = "wifisense.security.jwt-secret=test-secret-that-is-at-least-32-bytes-long")
class AgentControllerTest {

    private static final String KEY = "wsa_" + "k".repeat(43);
    private static final String REPORT = """
            {"agentVersion":"1.0.0","authentication":"WPA2","band":"5","channel":44,"signalDbm":-58,
             "latencyMs":21.5,"jitterMs":2,"packetLossPct":0,"deviceCount":2,
             "deviceHashes":["aaaaaaaaaaaaaaaaaaaaaaaa","bbbbbbbbbbbbbbbbbbbbbbbb"],"openPorts":[80,443]}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AgentReportService service;
    @MockitoBean
    private AgentPairingService pairing;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void theAgentReportsWithItsKeyAndNoSession() throws Exception {
        when(service.receive(eq(KEY), any())).thenReturn(new AgentReportAck(9L, 2, 0, 90, "SECURE", false,
                "2 equipos conectados."));

        mvc.perform(post("/api/agent/reports").header("X-Agent-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(REPORT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.securityGrade").value("SECURE"))
                .andExpect(jsonPath("$.summary").value("2 equipos conectados."));
    }

    @Test
    void anInvalidKeyIsUnauthorized() throws Exception {
        when(service.receive(any(), any())).thenThrow(new InvalidAgentKeyException());

        mvc.perform(post("/api/agent/reports").contentType(MediaType.APPLICATION_JSON).content(REPORT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("La clave del agente no es válida o fue revocada."));
    }

    @Test
    void tooManyReportsSayWhenToRetry() throws Exception {
        when(service.receive(any(), any())).thenThrow(new AgentReportLimitException(42));

        mvc.perform(post("/api/agent/reports").header("X-Agent-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(REPORT))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"));
    }

    @Test
    void forgedValuesAreRejectedBeforeReachingTheService() throws Exception {
        mvc.perform(post("/api/agent/reports").header("X-Agent-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"agentVersion":"1.0.0<script>","authentication":"ROOT","signalDbm":30,
                                 "deviceCount":5000,"deviceHashes":["9c:a2:f4:00:00:02"],"openPorts":[70000]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.authentication").value("Tipo de seguridad desconocido."))
                .andExpect(jsonPath("$.errors.signalDbm").value("La señal debe estar entre -100 y 0 dBm."))
                .andExpect(jsonPath("$.errors.agentVersion").value("Versión del agente inválida."));
        verifyNoInteractions(service);
    }

    @Test
    void onlyPostingReportsIsOpen() throws Exception {
        mvc.perform(get("/api/agent/reports")).andExpect(status().isUnauthorized());
    }

    @Test
    void theInstallerExchangesACodeWithoutASessionAndTheKeyIsNotCached() throws Exception {
        when(pairing.pair(eq("K7QM-4RZD"), any())).thenReturn(
                new com.wifisense.dto.AgentPairResponse("wsa_" + "k".repeat(43), "wsa_kkkk", "Casa"));

        mvc.perform(post("/api/agent/pair").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"K7QM-4RZD\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.networkName").value("Casa"));
    }

    @Test
    void aWrongCodeIsExplainedInSpanish() throws Exception {
        when(pairing.pair(any(), any())).thenThrow(new com.wifisense.service.InvalidPairingCodeException());

        mvc.perform(post("/api/agent/pair").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("El código no es válido")));
    }
}
