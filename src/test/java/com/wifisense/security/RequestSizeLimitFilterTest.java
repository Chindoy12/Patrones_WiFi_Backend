package com.wifisense.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RequestSizeLimitFilterTest {

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter("1MB");

    /** A chain that reads the whole body, like a controller would. */
    private static FilterChain reading(AtomicLong read) {
        return (request, response) -> {
            try (InputStream in = request.getInputStream()) {
                read.set(in.readAllBytes().length);
            }
        };
    }

    private static MockHttpServletRequest post(String uri, int bodyBytes, boolean declareLength) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri) {
            @Override
            public long getContentLengthLong() {
                return declareLength ? bodyBytes : -1;  // -1 = sent in chunks, size unknown
            }
        };
        request.setContent(new byte[bodyBytes]);
        return request;
    }

    @Test
    void declaredOversizedBodyIsRejectedBeforeReading() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicLong read = new AtomicLong(-1);

        filter.doFilter(post("/api/auth/login", 20_000, true), response, reading(read));

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(read.get()).isEqualTo(-1);
    }

    @Test
    void chunkedBodyIsStoppedWhenItPassesTheLimit() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(post("/api/auth/login", 20_000, false), response, reading(new AtomicLong()));

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("La solicitud es demasiado grande.");
    }

    @Test
    void speedTestUploadMayCarryUpToItsOwnLimit() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicLong read = new AtomicLong();

        filter.doFilter(post(RequestSizeLimitFilter.UPLOAD_PATH, 500_000, false), response, reading(read));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(read.get()).isEqualTo(500_000);
    }

    @Test
    void anAgentReportWith256DevicesFits() throws Exception {
        // 256 devices of agent 1.7.0 (fingerprint, maker, name, model, kind) plus the old list of fingerprints
        StringBuilder devices = new StringBuilder();
        for (int i = 0; i < 256; i++) {
            devices.append(String.format("{\"id\":\"%024x\",\"vendor\":\"Samsung Electronics\",\"name\":"
                    + "\"Galaxy-S23-Ultra-de-Ana-Maria-%03d\",\"privateMac\":true,\"model\":\"SM-S918B\","
                    + "\"kind\":\"PHONE\"},\"%024x\",", i, i, i));
        }
        int size = devices.length() + 4_000;  // the rest of the report (Wi-Fi, channels, ports, traffic)
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicLong read = new AtomicLong();

        filter.doFilter(post(RequestSizeLimitFilter.AGENT_REPORT_PATH, size, true), response, reading(read));

        assertThat(size).isGreaterThan(16 * 1024);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(read.get()).isEqualTo(size);
    }

    @Test
    void anAgentReportBeyondItsLimitIsRejected() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(post(RequestSizeLimitFilter.AGENT_REPORT_PATH, 200_000, false), response,
                reading(new AtomicLong()));

        assertThat(response.getStatus()).isEqualTo(413);
    }

    @Test
    void uploadBeyondItsLimitIsRejectedToo() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(post(RequestSizeLimitFilter.UPLOAD_PATH, 1_200_000, false), response, reading(new AtomicLong()));

        assertThat(response.getStatus()).isEqualTo(413);
    }

    @Test
    void readerIsLimitedAsWell() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = post("/api/auth/register", 20_000, false);

        filter.doFilter(request, response, (req, res) -> {
            char[] buffer = new char[4096];
            while (req.getReader().read(buffer) != -1) {
                // a JSON parser reading the whole body
            }
        });

        assertThat(response.getStatus()).isEqualTo(413);
    }
}
