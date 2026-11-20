package com.wifisense.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * One report of the WiFiSense agent with what the AI concluded about it: from a home of Mi Wi-Fi ({@link AgentReport})
 * or from the sensor of a team network ({@link SensorReport}). Every measurement is optional. The two are stored in
 * their own tables, but what a report holds and how it is filled is the same, so it lives here once.
 */
@MappedSuperclass
public abstract class WifiReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "agent_version", nullable = false, length = 20)
    private String agentVersion;

    @Column(length = 64)
    private String ssid;

    @Column(nullable = false, length = 10)
    private String authentication;

    @Column(length = 3)
    private String band;

    private Short channel;

    @Column(name = "signal_dbm")
    private Short signalDbm;

    @Column(name = "latency_ms")
    private Double latencyMs;

    @Column(name = "jitter_ms")
    private Double jitterMs;

    @Column(name = "packet_loss_pct")
    private Double packetLossPct;

    @Column(name = "gateway_latency_ms")
    private Double gatewayLatencyMs;

    @Column(name = "device_count", nullable = false)
    private short deviceCount;

    @Column(name = "new_device_count", nullable = false)
    private short newDeviceCount;

    @Column(name = "security_score")
    private Short securityScore;

    @Column(name = "security_grade", length = 10)
    private String securityGrade;

    @Column(name = "quality_score")
    private Short qualityScore;

    /** Open ports, Wi-Fi extras and the AI answers (security findings, quality, channel...) as the page shows them. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String details = "{}";

    protected WifiReport() {
    }

    protected WifiReport(Instant receivedAt, String agentVersion) {
        this.receivedAt = receivedAt;
        this.agentVersion = agentVersion;
        this.authentication = "UNKNOWN";
    }

    public void wifi(String ssid, String authentication, String band, Integer channel, Integer signalDbm) {
        this.ssid = ssid;
        this.authentication = authentication == null ? "UNKNOWN" : authentication;
        this.band = band;
        this.channel = channel == null ? null : channel.shortValue();
        this.signalDbm = signalDbm == null ? null : signalDbm.shortValue();
    }

    public void connection(Double latencyMs, Double jitterMs, Double packetLossPct, Double gatewayLatencyMs) {
        this.latencyMs = latencyMs;
        this.jitterMs = jitterMs;
        this.packetLossPct = packetLossPct;
        this.gatewayLatencyMs = gatewayLatencyMs;
    }

    public void devices(int count, int newCount) {
        this.deviceCount = (short) count;
        this.newDeviceCount = (short) Math.min(newCount, count);
    }

    public void security(int score, String grade) {
        this.securityScore = (short) score;
        this.securityGrade = grade;
    }

    public void quality(int score) {
        this.qualityScore = (short) score;
    }

    public void details(String json) {
        this.details = json;
    }

    /** Whether the AI found an anomaly; null when this kind of report does not ask it (the sensor uses alerts). */
    public Boolean getAnomalyDetected() { return null; }

    /** Type of the anomaly the AI found; null when it was not asked. */
    public String getAnomalyType() { return null; }

    public Long getId() { return id; }
    public Instant getReceivedAt() { return receivedAt; }
    public String getAgentVersion() { return agentVersion; }
    public String getSsid() { return ssid; }
    public String getAuthentication() { return authentication; }
    public String getBand() { return band; }
    public Integer getChannel() { return channel == null ? null : channel.intValue(); }
    public Integer getSignalDbm() { return signalDbm == null ? null : signalDbm.intValue(); }
    public Double getLatencyMs() { return latencyMs; }
    public Double getJitterMs() { return jitterMs; }
    public Double getPacketLossPct() { return packetLossPct; }
    public Double getGatewayLatencyMs() { return gatewayLatencyMs; }
    public int getDeviceCount() { return deviceCount; }
    public int getNewDeviceCount() { return newDeviceCount; }
    public Integer getSecurityScore() { return securityScore == null ? null : securityScore.intValue(); }
    public String getSecurityGrade() { return securityGrade; }
    public Integer getQualityScore() { return qualityScore == null ? null : qualityScore.intValue(); }
    public String getDetails() { return details; }
}
