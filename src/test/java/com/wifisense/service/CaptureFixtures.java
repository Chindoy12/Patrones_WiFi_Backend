package com.wifisense.service;

import com.wifisense.dto.AgentCaptureResult;
import com.wifisense.model.Network;
import com.wifisense.model.TrafficCapture;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Shared pieces of the capture tests: the configuration, a team network and captures in each state. */
public final class CaptureFixtures {

    public static final Instant NOW = Instant.parse("2026-10-26T21:00:00Z");
    public static final CaptureProperties PROPERTIES = new CaptureProperties(30, 5000, 120, 20000, DataSize.ofMegabytes(20),
            Duration.ofMinutes(3), Duration.ofSeconds(90), Duration.ofSeconds(10), "2.0.0", Duration.ofDays(90));
    public static final TrafficCapture.CaptureLimits LIMITS = new TrafficCapture.CaptureLimits(30, 5000, 20L << 20);

    private CaptureFixtures() {
    }

    public static Network network(long id) {
        Network network = mock(Network.class);
        when(network.getId()).thenReturn(id);
        return network;
    }

    public static TrafficCapture pending(Network network, long id, Instant requestedAt) {
        TrafficCapture capture = new TrafficCapture(network, 1L, "Revisar la lentitud de la sala", LIMITS, requestedAt);
        ReflectionTestUtils.setField(capture, "id", id);
        return capture;
    }

    public static TrafficCapture running(Network network, long id, Instant startedAt) {
        TrafficCapture capture = pending(network, id, startedAt.minusSeconds(5));
        capture.start(startedAt);
        return capture;
    }

    /** A plausible result: 1200 packets in 30 s, mostly HTTPS, with some DNS, ARP and mDNS. */
    public static AgentCaptureResult completed(long captureId) {
        return new AgentCaptureResult(captureId, "COMPLETED", null, "DURATION", "PKTMON", "Wi-Fi", "2.0.0", 30_200,
                1200, 840_000L, 0, 2, 600_000L, 240_000L, 40, 80, 6, 25,
                List.of(new AgentCaptureResult.Protocol("HTTPS", 900, 780_000L),
                        new AgentCaptureResult.Protocol("DNS", 120, 12_000L),
                        new AgentCaptureResult.Protocol("ARP", 40, 2_400L),
                        new AgentCaptureResult.Protocol("MDNS", 80, 20_000L)),
                List.of(new AgentCaptureResult.Port("TCP", 443, 900, 780_000L),
                        new AgentCaptureResult.Port("UDP", 53, 120, 12_000L)),
                java.util.stream.IntStream.range(0, 30)
                        .mapToObj(s -> new AgentCaptureResult.Interval(s, 40, 28_000L)).toList());
    }

    public static AgentCaptureResult failed(long captureId, String reason) {
        return new AgentCaptureResult(captureId, "FAILED", reason, null, null, null, "2.0.0", null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }
}
