package com.wifisense.model;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** A device the agent has seen on a home network, known only by a salted fingerprint of its MAC address. */
@Entity
@Table(name = "agent_devices")
@IdClass(AgentDevice.Key.class)
public class AgentDevice {

    @Id
    @Column(name = "home_network_id")
    private Long networkId;

    @Id
    @Column(columnDefinition = "CHAR(24)", length = 24)
    private String fingerprint;

    @Column(length = 40)
    private String label;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected AgentDevice() {
    }

    public AgentDevice(Long networkId, String fingerprint, Instant seenAt) {
        this.networkId = networkId;
        this.fingerprint = fingerprint;
        this.firstSeenAt = seenAt;
        this.lastSeenAt = seenAt;
    }

    public void seen(Instant at) {
        if (at.isAfter(lastSeenAt)) {
            this.lastSeenAt = at;
        }
    }

    public void rename(String label) {
        this.label = label;
    }

    public Long getNetworkId() { return networkId; }
    public String getFingerprint() { return fingerprint; }
    public String getLabel() { return label; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }

    public static class Key implements Serializable {
        private Long networkId;
        private String fingerprint;

        protected Key() {
        }

        public Key(Long networkId, String fingerprint) {
            this.networkId = networkId;
            this.fingerprint = fingerprint;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(networkId, key.networkId)
                    && Objects.equals(fingerprint, key.fingerprint);
        }

        @Override
        public int hashCode() {
            return Objects.hash(networkId, fingerprint);
        }
    }
}
