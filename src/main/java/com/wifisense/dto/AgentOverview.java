package com.wifisense.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.wifisense.model.DeviceIdentification;
import com.wifisense.model.NameSource;
import com.wifisense.model.ObservedDevice;
import com.wifisense.model.WifiReport;

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

        /** The view of a stored report of a home or of a team network; {@code details} is its JSON, already read. */
        public static Report of(WifiReport r, JsonNode details) {
            return new Report(r.getId(), r.getReceivedAt(), r.getAgentVersion(), r.getSsid(), r.getAuthentication(),
                    r.getBand(), r.getChannel(), r.getSignalDbm(), r.getLatencyMs(), r.getJitterMs(),
                    r.getPacketLossPct(), r.getGatewayLatencyMs(), r.getDeviceCount(), r.getNewDeviceCount(),
                    r.getSecurityScore(), r.getSecurityGrade(), r.getQualityScore(), r.getAnomalyDetected(),
                    r.getAnomalyType(), details);
        }
    }

    public record HistoryPoint(Instant at, Double latencyMs, int deviceCount, Integer securityScore,
                               Integer qualityScore, Boolean anomalyDetected) {

        public static HistoryPoint of(WifiReport r) {
            return new HistoryPoint(r.getReceivedAt(), r.getLatencyMs(), r.getDeviceCount(), r.getSecurityScore(),
                    r.getQualityScore(), r.getAnomalyDetected());
        }
    }

    /**
     * {@code privateMac}: uses a random address, so it is almost surely a phone or tablet. {@code online}: present in
     * the latest report. {@code recent}: joined in the last 24 hours. {@code kind}: PHONE, COMPUTER, TV... (a guess of
     * the agent). {@code role}: SELF (the computer the agent runs on) or ROUTER. {@code model}: the model the device
     * announces. {@code nameSource}: where the name came from (agent 2.0.0). {@code identification}: how well the
     * device is identified, from ALIAS (the user named it) to UNKNOWN (see {@link DeviceIdentification}).
     */
    public record Device(String id, String label, String vendor, String hostname, boolean privateMac,
                         boolean authorized, Instant firstSeenAt, Instant lastSeenAt, boolean online, boolean recent,
                         String kind, String role, String model, NameSource nameSource,
                         DeviceIdentification identification) {

        public Device(String id, String label, String vendor, String hostname, boolean privateMac, boolean authorized,
                      Instant firstSeenAt, Instant lastSeenAt, boolean online, boolean recent, String kind,
                      String role, String model, NameSource nameSource) {
            this(id, label, vendor, hostname, privateMac, authorized, firstSeenAt, lastSeenAt, online, recent, kind,
                    role, model, nameSource, DeviceIdentification.of(label, hostname, nameSource, vendor, kind));
        }

        /** The view of a stored device of a home or of a team network. */
        public static Device of(ObservedDevice d, boolean online, boolean recent) {
            return new Device(d.getFingerprint(), d.getLabel(), d.getVendor(), d.getHostname(), d.isPrivateMac(),
                    d.isAuthorized(), d.getFirstSeenAt(), d.getLastSeenAt(), online, recent, d.getKind(), d.getRole(),
                    d.getModel(), d.getNameSource());
        }

        public Device(String id, String label, String vendor, String hostname, boolean privateMac, boolean authorized,
                      Instant firstSeenAt, Instant lastSeenAt, boolean online, boolean recent, String kind,
                      String role, String model) {
            this(id, label, vendor, hostname, privateMac, authorized, firstSeenAt, lastSeenAt, online, recent, kind,
                    role, model, null);
        }

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
