package com.wifisense.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * One report of the sensor of a team network (like {@link AgentReport} for Mi Wi-Fi). The measurement itself is also
 * stored in measurements; this keeps what that table has no place for.
 */
@Entity
@Table(name = "network_sensor_reports")
public class SensorReport extends WifiReport {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "network_id", nullable = false, updatable = false)
    private Network network;

    protected SensorReport() {
    }

    public SensorReport(Network network, Instant receivedAt, String agentVersion) {
        super(receivedAt, agentVersion);
        this.network = network;
    }

    public Network getNetwork() { return network; }
}
