package com.wifisense.controller;

import com.wifisense.dto.AgentReportAck;
import com.wifisense.dto.AgentScanTask;
import com.wifisense.model.HomeNetwork;
import com.wifisense.model.ScanOrigin;
import com.wifisense.model.WifiScanSession;
import com.wifisense.security.SecurityConfig;
import com.wifisense.security.SecurityProperties;
import com.wifisense.service.AgentPairingService;
import com.wifisense.service.AgentReportLimitException;
import com.wifisense.service.AgentReportService;
import com.wifisense.service.AgentScanService;
import com.wifisense.service.InvalidAgentKeyException;
import com.wifisense.service.SensorReportService;
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

import java.time.Instant;
import java.util.Optional;

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
    private SensorReportService sensors;
    @MockitoBean
    private AgentScanService scans;
    @MockitoBean
    private com.wifisense.service.AgentCaptureService captures;
    @MockitoBean
    private com.wifisense.service.CaptureAnalysisService captureAnalysis;
    @MockitoBean
    private com.wifisense.service.AgentCapabilitiesService capabilities;
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
    void theSensorOfATeamNetworkReportsToTheSameAddress() throws Exception {
        when(sensors.receive(eq(KEY), any())).thenReturn(Optional.of(new AgentReportAck(31L, 2, 0, null, null, false,
                "Sensor de «Biblioteca»: medición guardada. Estado: normal.")));

        mvc.perform(post("/api/agent/reports").header("X-Agent-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(REPORT.replace("\"openPorts\":[80,443]}", "\"openPorts\":[80,443],"
                                + "\"trafficBytes\":5242880,\"trafficPackets\":4100,"
                                + "\"protocolPackets\":[{\"protocol\":\"TCP\",\"packets\":3900}]}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary").value("Sensor de «Biblioteca»: medición guardada. Estado: normal."));
        verifyNoInteractions(service);
    }

    @Test
    void aProtocolTheAgentCannotCountIsRejected() throws Exception {
        mvc.perform(post("/api/agent/reports").header("X-Agent-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(REPORT.replace("\"openPorts\":[80,443]}",
                                "\"protocolPackets\":[{\"protocol\":\"HTTP/9\",\"packets\":-1}]}")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service, sensors);
    }

    @Test
    void theAgentIsToldWhenToReportAgainAndWhatEachDeviceIs() throws Exception {
        when(service.receive(eq(KEY), any())).thenReturn(new AgentReportAck(9L, 2, 0, 90, "SECURE", false,
                "2 equipos conectados.", 60));

        mvc.perform(post("/api/agent/reports").header("X-Agent-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(REPORT.replace("\"openPorts\":[80,443]}", "\"devices\":[{\"id\":\"aaaaaaaaaaaaaaaaaaaaaaaa\","
                                + "\"name\":\"iPhone de Ana\",\"model\":\"iPhone15,2\",\"kind\":\"PHONE\"},"
                                + "{\"id\":\"bbbbbbbbbbbbbbbbbbbbbbbb\",\"kind\":\"COMPUTER\",\"role\":\"SELF\"}]}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nextReportSeconds").value(60));
    }

    @Test
    void anUnknownKindOrRoleOfDeviceIsRejected() throws Exception {
        mvc.perform(post("/api/agent/reports").header("X-Agent-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(REPORT.replace("\"openPorts\":[80,443]}", "\"devices\":[{\"id\":\"aaaaaaaaaaaaaaaaaaaaaaaa\","
                                + "\"kind\":\"TOASTER\",\"role\":\"ADMIN\"}]}")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service, sensors);
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

    @Test
    void theAnswerToAReportCarriesTheScanToRunNow() throws Exception {
        when(service.receive(eq(KEY), any())).thenReturn(new AgentReportAck(9L, 2, 0, 90, "SECURE", false,
                "2 equipos conectados."));
        when(scans.dueWithReport(KEY)).thenReturn(Optional.of(50L));

        mvc.perform(post("/api/agent/reports").header("X-Agent-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(REPORT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scanId").value(50));
    }

    @Test
    void aProblemWithTheScansNeverTurnsASavedReportIntoAnError() throws Exception {
        when(service.receive(eq(KEY), any())).thenReturn(new AgentReportAck(9L, 2, 0, 90, "SECURE", false,
                "2 equipos conectados."));
        when(scans.dueWithReport(KEY)).thenThrow(new IllegalStateException("database down"));

        mvc.perform(post("/api/agent/reports").header("X-Agent-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(REPORT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportId").value(9))
                .andExpect(jsonPath("$.scanId").doesNotExist());
    }

    @Test
    void theSensorOfATeamNetworkIsNeverAskedToScan() throws Exception {
        when(sensors.receive(eq(KEY), any())).thenReturn(Optional.of(new AgentReportAck(31L, 2, 0, null, null, false,
                "Sensor.")));

        mvc.perform(post("/api/agent/reports").header("X-Agent-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(REPORT))
                .andExpect(status().isCreated());
        verifyNoInteractions(scans);
    }

    @Test
    void betweenReportsTheAgentAsksForAScanWithItsKeyAndNoSession() throws Exception {
        when(scans.next(KEY)).thenReturn(Optional.of(new AgentScanTask(50L)), Optional.empty());

        mvc.perform(get("/api/agent/scans/next").header("X-Agent-Key", KEY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scanId").value(50));
        mvc.perform(get("/api/agent/scans/next").header("X-Agent-Key", KEY)).andExpect(status().isNoContent());
    }

    @Test
    void theAgentDeliversWhatItSaw() throws Exception {
        WifiScanSession scan = new WifiScanSession(mock(HomeNetwork.class), ScanOrigin.MANUAL, null,
                Instant.parse("2026-10-26T20:00:00Z"));
        scan.complete(Instant.parse("2026-10-26T20:00:05Z"),
                new WifiScanSession.ScanDetails("Wi-Fi", "NETSH", true, 4000, "1.9.0"), 1, 1);
        when(scans.receive(eq(KEY), any())).thenReturn(scan);

        mvc.perform(post("/api/agent/scans").header("X-Agent-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scanId":50,"status":"COMPLETED","interfaceName":"Wi-Fi","scanTool":"NETSH",
                                 "fresh":true,"durationMs":4000,"agentVersion":"1.9.0",
                                 "accessPoints":[{"bssid":"aaaaaaaaaaaaaaaaaaaaaaaa","bssidSuffix":"F1",
                                   "ssid":"Café ☕","signalPercent":72,"channel":6,"band":"2.4",
                                   "authentication":"WPA2","securityLabel":"WPA2-Personal","cipher":"CCMP"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void aScanResultWithTheBssidOrForgedValuesIsRejected() throws Exception {
        mvc.perform(post("/api/agent/scans").header("X-Agent-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scanId":50,"status":"DONE","failureReason":"HACKED","scanTool":"IWLIST",
                                 "accessPoints":[{"bssid":"a4:2b:b0:12:34:56","bssidSuffix":"12:34",
                                   "signalPercent":180,"channel":0,"band":"60","authentication":"WPA9"}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").value("Estado de escaneo desconocido."))
                .andExpect(jsonPath("$.errors['accessPoints[0].bssid']").value("Huella de BSSID inválida."));
        verifyNoInteractions(scans);
    }
}
