package com.wifisense.speedtest;

import com.wifisense.security.SecurityConfig;
import com.wifisense.security.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SpeedTestController.class)
@Import({SecurityConfig.class, SpeedTestControllerTest.SameThreadStreaming.class})
@EnableConfigurationProperties({SecurityProperties.class, SpeedTestProperties.class})
@TestPropertySource(properties = {"wifisense.security.jwt-secret=test-secret-that-is-at-least-32-bytes-long",
        "wifisense.speedtest.max-download=1MB"})
class SpeedTestControllerTest {

    /**
     * The download is streamed by another thread. Under MockMvc that thread and the security filters still finishing on
     * the test thread both touch the same (not thread-safe) mock response, which failed about once in 150 runs
     * (ConcurrentModificationException). Here the body is written on the request thread; a real server is not affected.
     */
    @TestConfiguration
    static class SameThreadStreaming implements WebMvcConfigurer {
        @Override
        public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
            configurer.setTaskExecutor(new TaskExecutorAdapter(Runnable::run));
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SpeedTestThrottle throttle;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private static RequestPostProcessor as(String username, String role) {
        return jwt().jwt(token -> token.subject(username)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private byte[] download(String query) throws Exception {
        MvcResult started = mvc.perform(get("/api/my/speedtest/download" + query).with(as("ana", "USER")))
                .andExpect(request().asyncStarted()).andReturn();
        return mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, no-transform"))
                .andReturn().getResponse().getContentAsByteArray();
    }

    @Test
    void pingAnswersWithoutBodyAndWithoutCache() throws Exception {
        mvc.perform(get("/api/my/speedtest/ping").with(as("ana", "USER")))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void downloadSendsTheRequestedBytesWithinLimits() throws Exception {
        assertThat(download("?bytes=300000")).hasSize(300_000);
        assertThat(download("?bytes=999999999")).hasSize(1024 * 1024);
        assertThat(download("?bytes=1")).hasSize(SpeedTestController.MIN_DOWNLOAD_BYTES);
        verify(throttle, times(3)).acquireTransfer("ana");
    }

    @Test
    void uploadIsReadAndDiscarded() throws Exception {
        mvc.perform(post("/api/my/speedtest/upload").with(as("ana", "USER"))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(new byte[200_000]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedBytes").value(200_000));
        verify(throttle).acquireTransfer("ana");
    }

    @Test
    void overBudgetAnswersTooManyRequestsInSpanish() throws Exception {
        doThrow(new SpeedTestLimitException()).when(throttle).acquireTransfer("ana");

        mvc.perform(get("/api/my/speedtest/download").with(as("ana", "USER")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Hiciste muchas pruebas seguidas. Espera unos minutos e inténtalo de nuevo."));
    }

    @Test
    void onlyEndUsersCanMeasure() throws Exception {
        mvc.perform(get("/api/my/speedtest/ping").with(as("jefe", "ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/my/speedtest/ping")).andExpect(status().isUnauthorized());
        verifyNoInteractions(throttle);
    }
}
