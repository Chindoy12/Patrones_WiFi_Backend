package com.wifisense.model;

import jakarta.persistence.*;

import java.time.Instant;

/** One report of the agent of a home network, which the AI also checks for anomalies. */
@Entity
@Table(name = "agent_reports")
public class AgentReport extends WifiReport {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_network_id", nullable = false, updatable = false)
    private HomeNetwork network;

    @Column(name = "anomaly_detected")
    private Boolean anomalyDetected;

    @Column(name = "anomaly_type", length = 20)
    private String anomalyType;

    protected AgentReport() {
    }

    public AgentReport(HomeNetwork network, Instant receivedAt, String agentVersion) {
        super(receivedAt, agentVersion);
        this.network = network;
    }

    public void anomaly(boolean detected, String type) {
        this.anomalyDetected = detected;
        this.anomalyType = type;
    }

    public HomeNetwork getNetwork() { return network; }

    @Override
    public Boolean getAnomalyDetected() { return anomalyDetected; }

    @Override
    public String getAnomalyType() { return anomalyType; }
}
