package com.wifisense.analysis;

import java.util.List;

/**
 * Contract of POST /api/v1/traffic/analyze in WiFiSense-ai: the statistical summary of a completed capture (never a
 * packet). {@code connectionLossPct} is the packet loss of the network's latest connection test, sent apart so the
 * AI never confuses it with {@code droppedByTool} (what the capture tool could not keep).
 */
public record AiTrafficRequest(double durationSeconds, int packets, long bytes, Integer droppedByTool,
                               Integer malformed, Long inboundBytes, Long outboundBytes, Integer broadcast,
                               Integer multicast, Integer localHosts, Integer remoteHosts, String stopReason,
                               Double connectionLossPct, List<Protocol> protocols, List<Port> ports,
                               List<Interval> intervals) {

    public record Protocol(String protocol, int packets, long bytes) {
    }

    public record Port(String transport, int port, int packets, long bytes) {
    }

    public record Interval(int second, int packets, long bytes) {
    }
}
