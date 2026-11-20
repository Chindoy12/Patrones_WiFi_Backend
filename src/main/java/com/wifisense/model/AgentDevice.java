package com.wifisense.model;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;

/** A device the agent has seen on a home network of Mi Wi-Fi; its owner can name it and mark it as not theirs. */
@Entity
@Table(name = "agent_devices")
@IdClass(ObservedDevice.Key.class)
@AttributeOverride(name = "networkId", column = @Column(name = "home_network_id"))
public class AgentDevice extends ObservedDevice {

    protected AgentDevice() {
    }

    public AgentDevice(Long networkId, String fingerprint, Instant seenAt) {
        super(networkId, fingerprint, seenAt);
    }
}
