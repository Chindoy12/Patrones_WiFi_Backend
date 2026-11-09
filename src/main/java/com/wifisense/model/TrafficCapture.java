package com.wifisense.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A technical capture on a team network, asked by an administrator with a purpose and limits, and run by that
 * network's sensor. It keeps only statistics of the packets the sensor really captured: no address, no MAC and no
 * content. The moves of its life are checked here, so no code path can, for example, complete a cancelled capture.
 */
@Entity
@Table(name = "traffic_captures")
public class TrafficCapture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "network_id", nullable = false, updatable = false)
    private Network network;

    /** The administrator who asked; forgotten if the account is deleted (the audit keeps the name). */
    @Column(name = "requested_by", updatable = false)
    private Long requestedBy;

    @Column(nullable = false, length = 200, updatable = false)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CaptureStatus status = CaptureStatus.PENDING;

    @Column(name = "max_seconds", nullable = false, updatable = false)
    private int maxSeconds;

    @Column(name = "max_packets", nullable = false, updatable = false)
    private int maxPackets;

    @Column(name = "max_bytes", nullable = false, updatable = false)
    private long maxBytes;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "cancel_requested_at")
    private Instant cancelRequestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 20)
    private CaptureFailure failureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_reason", length = 14)
    private CaptureStopReason stopReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "capture_tool", length = 14)
    private CaptureTool captureTool;

    @Column(name = "interface_name", length = 64)
    private String interfaceName;

    @Column(name = "agent_version", length = 20)
    private String agentVersion;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "packet_count")
    private Integer packetCount;

    @Column(name = "byte_count")
    private Long byteCount;

    @Column(name = "dropped_by_tool")
    private Integer droppedByTool;

    @Column(name = "malformed_packets")
    private Integer malformedPackets;

    @Column(name = "inbound_bytes")
    private Long inboundBytes;

    @Column(name = "outbound_bytes")
    private Long outboundBytes;

    @Column(name = "broadcast_packets")
    private Integer broadcastPackets;

    @Column(name = "multicast_packets")
    private Integer multicastPackets;

    @Column(name = "distinct_local_hosts")
    private Integer distinctLocalHosts;

    @Column(name = "distinct_remote_hosts")
    private Integer distinctRemoteHosts;

    @Enumerated(EnumType.STRING)
    @Column(name = "analyzed_with", length = 6)
    private FindingSource analyzedWith;

    @ElementCollection
    @CollectionTable(name = "traffic_capture_protocols", joinColumns = @JoinColumn(name = "capture_id"))
    @OrderBy("packets DESC")
    private List<ProtocolCount> protocols = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "traffic_capture_ports", joinColumns = @JoinColumn(name = "capture_id"))
    @OrderBy("packets DESC")
    private List<PortCount> ports = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "traffic_capture_intervals", joinColumns = @JoinColumn(name = "capture_id"))
    @OrderBy("second ASC")
    private List<IntervalCount> intervals = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "traffic_capture_findings", joinColumns = @JoinColumn(name = "capture_id"))
    private List<CaptureFinding> findings = new ArrayList<>();

    protected TrafficCapture() {
    }

    public TrafficCapture(Network network, Long requestedBy, String purpose, CaptureLimits limits, Instant requestedAt) {
        this.network = network;
        this.requestedBy = requestedBy;
        this.purpose = purpose;
        this.maxSeconds = limits.seconds();
        this.maxPackets = limits.packets();
        this.maxBytes = limits.bytes();
        this.requestedAt = requestedAt;
    }

    /** The sensor picked the capture up and started capturing. */
    public void start(Instant now) {
        move(CaptureStatus.RUNNING);
        this.startedAt = later(now);
    }

    /**
     * An administrator asked to stop it. Waiting: it is cancelled now. Running: the sensor is told on its next check
     * and delivers what it captured.
     */
    public void requestCancel(Instant now) {
        if (!status.active()) {
            throw new IllegalStateException("Esa captura ya terminó.");
        }
        this.cancelRequestedAt = later(now);
        if (status == CaptureStatus.PENDING) {
            move(CaptureStatus.CANCELLED);
            this.finishedAt = cancelRequestedAt;
        }
    }

    public boolean cancelRequested() {
        return cancelRequestedAt != null;
    }

    /** The statistics of what the sensor captured, already checked against the limits. */
    public void complete(Instant now, CaptureStopReason reason, CaptureDetails details, CaptureTotals totals,
                         List<ProtocolCount> protocols, List<PortCount> ports, List<IntervalCount> intervals) {
        move(CaptureStatus.COMPLETED);
        describe(details);
        this.stopReason = reason;
        this.packetCount = totals.packets();
        this.byteCount = totals.bytes();
        this.droppedByTool = totals.droppedByTool();
        this.malformedPackets = totals.malformed();
        this.inboundBytes = totals.inboundBytes();
        this.outboundBytes = totals.outboundBytes();
        this.broadcastPackets = totals.broadcast();
        this.multicastPackets = totals.multicast();
        this.distinctLocalHosts = totals.localHosts();
        this.distinctRemoteHosts = totals.remoteHosts();
        this.protocols = new ArrayList<>(protocols);
        this.ports = new ArrayList<>(ports);
        this.intervals = new ArrayList<>(intervals);
        this.finishedAt = later(now);
        if (startedAt == null) {
            startedAt = finishedAt;
        }
    }

    public void fail(Instant now, CaptureFailure reason, CaptureDetails details) {
        move(CaptureStatus.FAILED);
        if (details != null) {
            describe(details);
        }
        this.failureReason = reason;
        this.finishedAt = later(now);
    }

    /** The conclusions of the analysis replace the previous ones (an analysis can be repeated). */
    public void analyzed(FindingSource with, List<CaptureFinding> findings) {
        if (status != CaptureStatus.COMPLETED) {
            throw new IllegalStateException("Solo se analiza una captura completada.");
        }
        this.analyzedWith = with;
        this.findings.clear();
        this.findings.addAll(findings);
    }

    private void describe(CaptureDetails details) {
        this.captureTool = details.tool();
        this.interfaceName = details.interfaceName();
        this.agentVersion = details.agentVersion();
        this.durationMs = details.durationMs();
    }

    private void move(CaptureStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException(status.active() ? "Esa captura no puede pasar a " + next + "."
                    : "Esa captura ya terminó.");
        }
        this.status = next;
    }

    /** A clock that went back must not break the "never before it was requested" rule of the table. */
    private Instant later(Instant now) {
        return now.isBefore(requestedAt) ? requestedAt : now;
    }

    /** Seconds, packets and bytes a capture may reach. */
    public record CaptureLimits(int seconds, int packets, long bytes) {
    }

    /** How the sensor captured: tool, interface, agent version and duration. */
    public record CaptureDetails(CaptureTool tool, String interfaceName, String agentVersion, Integer durationMs) {
    }

    /** The totals of a completed capture. */
    public record CaptureTotals(int packets, long bytes, Integer droppedByTool, Integer malformed, Long inboundBytes,
                                Long outboundBytes, Integer broadcast, Integer multicast, Integer localHosts,
                                Integer remoteHosts) {
    }

    public Long getId() { return id; }
    public Network getNetwork() { return network; }
    public Long getRequestedBy() { return requestedBy; }
    public String getPurpose() { return purpose; }
    public CaptureStatus getStatus() { return status; }
    public int getMaxSeconds() { return maxSeconds; }
    public int getMaxPackets() { return maxPackets; }
    public long getMaxBytes() { return maxBytes; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Instant getCancelRequestedAt() { return cancelRequestedAt; }
    public CaptureFailure getFailureReason() { return failureReason; }
    public CaptureStopReason getStopReason() { return stopReason; }
    public CaptureTool getCaptureTool() { return captureTool; }
    public String getInterfaceName() { return interfaceName; }
    public String getAgentVersion() { return agentVersion; }
    public Integer getDurationMs() { return durationMs; }
    public Integer getPacketCount() { return packetCount; }
    public Long getByteCount() { return byteCount; }
    public Integer getDroppedByTool() { return droppedByTool; }
    public Integer getMalformedPackets() { return malformedPackets; }
    public Long getInboundBytes() { return inboundBytes; }
    public Long getOutboundBytes() { return outboundBytes; }
    public Integer getBroadcastPackets() { return broadcastPackets; }
    public Integer getMulticastPackets() { return multicastPackets; }
    public Integer getDistinctLocalHosts() { return distinctLocalHosts; }
    public Integer getDistinctRemoteHosts() { return distinctRemoteHosts; }
    public FindingSource getAnalyzedWith() { return analyzedWith; }
    public List<ProtocolCount> getProtocols() { return protocols; }
    public List<PortCount> getPorts() { return ports; }
    public List<IntervalCount> getIntervals() { return intervals; }
    public List<CaptureFinding> getFindings() { return findings; }
}
