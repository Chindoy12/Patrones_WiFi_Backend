package com.wifisense.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * What the computer of an agent can do, as the agent checks it in every report: whether captures were allowed on
 * that computer, which capture tools it found, whether it runs as administrator and whether the Wi-Fi adapter
 * declares monitor mode. One row per agent: of a Mi Wi-Fi network or the sensor of a team network.
 */
@Entity
@Table(name = "agent_capabilities")
public class AgentCapabilities {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "home_network_id", updatable = false)
    private Long homeNetworkId;

    @Column(name = "network_id", updatable = false)
    private Long networkId;

    @Column(length = 20)
    private String platform;

    @Column(name = "agent_version", length = 20)
    private String agentVersion;

    @Column(nullable = false)
    private boolean elevated;

    @Column(name = "capture_consent", nullable = false)
    private boolean captureConsent;

    @Column(nullable = false)
    private boolean npcap;

    @Column(nullable = false)
    private boolean pktmon;

    @Column(name = "raw_socket", nullable = false)
    private boolean rawSocket;

    @Column(name = "packet_socket", nullable = false)
    private boolean packetSocket;

    @Enumerated(EnumType.STRING)
    @Column(name = "monitor_mode", nullable = false, length = 12)
    private MonitorMode monitorMode = MonitorMode.UNKNOWN;

    @Column(name = "capture_interfaces", nullable = false)
    private int captureInterfaces;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    protected AgentCapabilities() {
    }

    private AgentCapabilities(Long homeNetworkId, Long networkId) {
        this.homeNetworkId = homeNetworkId;
        this.networkId = networkId;
    }

    public static AgentCapabilities ofHome(Long homeNetworkId) {
        return new AgentCapabilities(homeNetworkId, null);
    }

    public static AgentCapabilities ofSensor(Long networkId) {
        return new AgentCapabilities(null, networkId);
    }

    /** What the agent reported now replaces what it reported before. */
    public void update(Snapshot snapshot, Instant at) {
        this.platform = snapshot.platform();
        this.agentVersion = snapshot.agentVersion();
        this.elevated = snapshot.elevated();
        this.captureConsent = snapshot.captureConsent();
        this.npcap = snapshot.npcap();
        this.pktmon = snapshot.pktmon();
        this.rawSocket = snapshot.rawSocket();
        this.packetSocket = snapshot.packetSocket();
        this.monitorMode = snapshot.monitorMode() == null ? MonitorMode.UNKNOWN : snapshot.monitorMode();
        this.captureInterfaces = Math.max(0, Math.min(64, snapshot.captureInterfaces()));
        this.reportedAt = at;
    }

    /** At least one capture tool the agent can actually use. */
    public boolean hasCaptureTool() {
        return npcap || pktmon || rawSocket || packetSocket;
    }

    /** The values of one report, already cleaned. */
    public record Snapshot(String platform, String agentVersion, boolean elevated, boolean captureConsent,
                           boolean npcap, boolean pktmon, boolean rawSocket, boolean packetSocket,
                           MonitorMode monitorMode, int captureInterfaces) {
    }

    public Long getId() { return id; }
    public Long getHomeNetworkId() { return homeNetworkId; }
    public Long getNetworkId() { return networkId; }
    public String getPlatform() { return platform; }
    public String getAgentVersion() { return agentVersion; }
    public boolean isElevated() { return elevated; }
    public boolean isCaptureConsent() { return captureConsent; }
    public boolean isNpcap() { return npcap; }
    public boolean isPktmon() { return pktmon; }
    public boolean isRawSocket() { return rawSocket; }
    public boolean isPacketSocket() { return packetSocket; }
    public MonitorMode getMonitorMode() { return monitorMode; }
    public int getCaptureInterfaces() { return captureInterfaces; }
    public Instant getReportedAt() { return reportedAt; }
}
