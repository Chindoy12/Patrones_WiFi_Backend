package com.wifisense.controller;

import com.wifisense.dto.AgentLiveAck;
import com.wifisense.dto.AgentLiveSample;
import com.wifisense.dto.AgentLiveTask;
import com.wifisense.security.SecurityConfig;
import com.wifisense.security.SecurityProperties;
import com.wifisense.service.InvalidAgentKeyException;
import com.wifisense.service.LiveService;
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

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AgentLiveController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = "wifisense.security.jwt-secret=test-secret-that-is-at-least-32-bytes-long")
class AgentLiveControllerTest {

    private static final String KEY = "wsa_" + "k".repeat(43);
    private static final String SAMPLE = """
            {"intervalMs":2000,"downloadBps":4000000,"uploadBps":250000,"internetMs":31.5,"gatewayMs":2.1,
             "signalPercent":71,"rxRateMbps":144.4,"txRateMbps":72.2}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LiveService live;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void theAgentAsksWithItsKeyAndNoSession() throws Exception {
        when(live.next(KEY)).thenReturn(Optional.of(new AgentLiveTask(true, 2, 600)));

        mvc.perform(get("/api/agent/live/next").header("X-Agent-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.live").value(true))
                .andExpect(jsonPath("$.sampleSeconds").value(2))
                .andExpect(jsonPath("$.maxSeconds").value(600));
    }

    @Test
    void nobodyWatchingIsNoContent() throws Exception {
        when(live.next(KEY)).thenReturn(Optional.empty());

        mvc.perform(get("/api/agent/live/next").header("X-Agent-Key", KEY))
                .andExpect(status().isNoContent());
    }

    @Test
    void aSampleIsReceivedAndTheAnswerSaysWhetherToGoOn() throws Exception {
        when(live.receive(eq(KEY), any())).thenReturn(new AgentLiveAck(false));

        mvc.perform(post("/api/agent/live/samples").header("X-Agent-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(SAMPLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keepGoing").value(false));
        verify(live).receive(KEY, new AgentLiveSample(2000, 4_000_000L, 250_000L, 31.5, null, 2.1, null, 71, 144.4,
                72.2));
    }

    @Test
    void numbersOutOfRangeAreRejected() throws Exception {
        mvc.perform(post("/api/agent/live/samples").header("X-Agent-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"signalPercent\":140,\"internetMs\":-3}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(live);
    }

    @Test
    void anUnknownKeyIs401() throws Exception {
        when(live.next("wsa_otra")).thenThrow(new InvalidAgentKeyException());

        mvc.perform(get("/api/agent/live/next").header("X-Agent-Key", "wsa_otra"))
                .andExpect(status().isUnauthorized());
    }
}
