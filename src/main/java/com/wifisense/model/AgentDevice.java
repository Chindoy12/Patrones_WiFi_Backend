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

    /** Device maker from the MAC OUI (Apple, Samsung...); null when the agent could not identify it. */
    @Column(length = 40)
    private String vendor;

    /** Name the device announces on the network ("iPhone-de-Ana"); null when it announces none. */
    @Column(length = 63)
    private String hostname;

    /** FALSE when the user marked the device as not theirs; it is then shown in red and keeps raising an alert. */
    @Column(nullable = false)
    private boolean authorized = true;

    /** TRUE when the device uses a random ("private") MAC: almost always a phone or a tablet. */
    @Column(name = "private_mac", nullable = false)
    private boolean privateMac;

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

    /** Keeps the first maker the agent reported and the latest name the device announced. */
    public void identify(String vendor, String hostname) {
        if (this.vendor == null && vendor != null) {
            this.vendor = vendor;
        }
        if (hostname != null && !hostname.isBlank()) {
            this.hostname = hostname;
        }
    }

    /** A MAC never stops being random for the same fingerprint, so the flag is only ever turned on. */
    public void markPrivateMac(boolean privateMac) {
        this.privateMac = this.privateMac || privateMac;
    }

    public void rename(String label) {
        this.label = label;
    }

    public void setAuthorized(boolean authorized) {
        this.authorized = authorized;
    }

    public Long getNetworkId() { return networkId; }
    public String getFingerprint() { return fingerprint; }
    public String getLabel() { return label; }
    public String getVendor() { return vendor; }
    public String getHostname() { return hostname; }
    public boolean isAuthorized() { return authorized; }
    public boolean isPrivateMac() { return privateMac; }
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
