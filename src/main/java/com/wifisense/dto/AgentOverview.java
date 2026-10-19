package com.wifisense.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

/** Everything Mi Wi-Fi shows about the agent of one network. */
public record AgentOverview(KeyInfo key, boolean online, Report latest, List<HistoryPoint> history,
                            List<Device> devices, List<Alert> alerts) {

    /**
     * The key without its secret part: how to recognise it and when the agent last reported. {@code identifyUntil}:
     * while in the future, the agent reports every minute so the page can tell which device is which.
     */
    public record KeyInfo(String prefix, Instant createdAt, Instant lastSeenAt, Instant identifyUntil) {

        public KeyInfo(String prefix, Instant createdAt, Instant lastSeenAt) {
            this(prefix, createdAt, lastSeenAt, null);
        }
    }

    public record Report(Long id, Instant receivedAt, String agentVersion, String ssid, String authentication,
                         String band, Integer channel, Integer signalDbm, Double latencyMs, Double jitterMs,
                         Double packetLossPct, Double gatewayLatencyMs, int deviceCount, int newDeviceCount,
                         Integer securityScore, String securityGrade, Integer qualityScore, Boolean anomalyDetected,
                         String anomalyType, JsonNode details) {
    }

    public record HistoryPoint(Instant at, Double latencyMs, int deviceCount, Integer securityScore,
                               Integer qualityScore, Boolean anomalyDetected) {
    }

    /**
     * {@code privateMac}: uses a random address, so it is almost surely a phone or tablet. {@code online}: present in
     * the latest report. {@code recent}: joined in the last 24 hours. {@code kind}: PHONE, COMPUTER, TV... (a guess of
     * the agent). {@code role}: SELF (the computer the agent runs on) or ROUTER. {@code model}: the model the device
     * announces.
     */
    public record Device(String id, String label, String vendor, String hostname, boolean privateMac,
                         boolean authorized, Instant firstSeenAt, Instant lastSeenAt, boolean online, boolean recent,
                         String kind, String role, String model) {

        public Device(String id, String label, String vendor, String hostname, boolean privateMac, boolean authorized,
                      Instant firstSeenAt, Instant lastSeenAt, boolean online, boolean recent) {
            this(id, label, vendor, hostname, privateMac, authorized, firstSeenAt, lastSeenAt, online, recent, null, null,
                    null);
        }
    }

    /** One past alert of this network. */
    public record Alert(String type, String detail, Instant at) {
    }
}
