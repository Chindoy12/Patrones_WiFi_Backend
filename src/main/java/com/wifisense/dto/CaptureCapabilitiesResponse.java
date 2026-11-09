package com.wifisense.dto;

import java.time.Instant;
import java.util.List;

/**
 * What the sensor of a team network can capture, before asking for anything: the sensor's state, what its computer
 * reported (consent, tools, permissions, monitor mode), the four capture scenarios with whether each one is
 * possible and why, every reason that blocks a capture now, and the limits an administrator may ask for.
 */
public record CaptureCapabilitiesResponse(
        boolean sensorInstalled,
        boolean sensorOnline,
        Instant lastReportAt,
        String agentVersion,
        String platform,
        boolean agentSupportsCapture,
        Instant checkedAt,
        Boolean elevated,
        Boolean consent,
        List<Tool> tools,
        String monitorMode,
        Integer interfaces,
        List<Scenario> scenarios,
        boolean canCapture,
        List<String> blockers,
        Limits limits) {

    /** A capture tool and whether the sensor's computer can use it. */
    public record Tool(String code, String label, boolean available, String note) {
    }

    /**
     * A capture scenario: LOCAL_HOST (A), SHARED_INTERFACE (B), MONITOR_MODE (C) or ROUTER (D). {@code status}:
     * AVAILABLE, UNAVAILABLE, NOT_VERIFIABLE (WiFiSense cannot know it) or NOT_IMPLEMENTED (possible in theory, not
     * done by WiFiSense).
     */
    public record Scenario(String code, String title, String status, String explanation) {
    }

    /** What the form proposes and the most it accepts. */
    public record Limits(int defaultSeconds, int maxSeconds, int defaultPackets, int maxPackets, long maxBytes) {
    }
}
