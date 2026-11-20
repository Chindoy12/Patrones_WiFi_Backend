package com.wifisense.model;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * A device the WiFiSense agent has seen on a network, known only by a salted fingerprint of its MAC address: a device
 * of a home of Mi Wi-Fi ({@link AgentDevice}) or of a team network seen by its sensor ({@link SensorDevice}). Both are
 * the same thing stored in two tables, so everything they know and do lives here once; each subclass only says its
 * table and the column of its network.
 */
@MappedSuperclass
public abstract class ObservedDevice implements TrackedDevice {

    /** Column network_id; {@link AgentDevice} maps it to home_network_id. */
    @Id
    @Column(name = "network_id")
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

    /** Where the name came from (CONFIRMED, ANNOUNCED, ROUTER, DNS); null without a name or from an old agent. */
    @Enumerated(EnumType.STRING)
    @Column(name = "name_source", length = 10)
    private NameSource nameSource;

    /** FALSE when its owner marked it as not authorized; it is then shown in red and keeps raising an alert. */
    @Column(nullable = false)
    private boolean authorized = true;

    /** TRUE when the device uses a random ("private") MAC: almost always a phone or a tablet. */
    @Column(name = "private_mac", nullable = false)
    private boolean privateMac;

    /** What kind of device the agent deduced (PHONE, COMPUTER, TV...): a guess; null when it could not tell. */
    @Column(length = 12)
    private String kind;

    /** SELF for the computer the agent runs on, ROUTER for the gateway of the network; null for any other. */
    @Column(length = 8)
    private String role;

    /** Model the device announces on the network ("MacBookPro18,1", "Chromecast"); null when it announces none. */
    @Column(length = 63)
    private String model;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected ObservedDevice() {
    }

    protected ObservedDevice(Long networkId, String fingerprint, Instant seenAt) {
        this.networkId = networkId;
        this.fingerprint = fingerprint;
        this.firstSeenAt = seenAt;
        this.lastSeenAt = seenAt;
    }

    @Override
    public void seen(Instant at) {
        if (at.isAfter(lastSeenAt)) {
            this.lastSeenAt = at;
        }
    }

    /** Keeps the first maker the agent reported and the latest name the device announced. */
    @Override
    public void identify(String vendor, String hostname) {
        if (this.vendor == null && vendor != null) {
            this.vendor = vendor;
        }
        if (hostname != null && !hostname.isBlank()) {
            this.hostname = hostname;
        }
    }

    @Override
    public void identify(String vendor, String hostname, NameSource source) {
        identify(vendor, hostname);
        if (hostname != null && !hostname.isBlank()) {
            this.nameSource = source;
        }
    }

    /** Keeps the latest kind, role and model the agent reported (it learns more as devices announce themselves). */
    @Override
    public void describe(String kind, String role, String model) {
        if (kind != null) {
            this.kind = kind;
        }
        if (role != null) {
            this.role = role;
        }
        if (model != null && !model.isBlank()) {
            this.model = model.length() > 63 ? model.substring(0, 63) : model;
        }
    }

    /** A MAC never stops being random for the same fingerprint, so the flag is only ever turned on. */
    @Override
    public void markPrivateMac(boolean privateMac) {
        this.privateMac = this.privateMac || privateMac;
    }

    public void rename(String label) {
        this.label = label;
    }

    /**
     * What the owner of the network can change: the name (blank removes it) and whether it is authorized. A null
     * leaves that part as it is.
     */
    public void edit(String label, Boolean authorized) {
        if (label != null) {
            String trimmed = label.trim();
            rename(trimmed.isEmpty() ? null : trimmed);
        }
        if (authorized != null) {
            authorize(authorized);
        }
    }

    /** False marks it as not authorized: it is shown in red and keeps raising an alert while it is connected. */
    public void authorize(boolean authorized) {
        this.authorized = authorized;
    }

    public Long getNetworkId() { return networkId; }
    @Override
    public String getFingerprint() { return fingerprint; }
    public String getLabel() { return label; }
    public String getVendor() { return vendor; }
    public String getHostname() { return hostname; }
    @Override
    public NameSource getNameSource() { return nameSource; }
    @Override
    public boolean isAuthorized() { return authorized; }
    public boolean isPrivateMac() { return privateMac; }
    public String getKind() { return kind; }
    public String getRole() { return role; }
    public String getModel() { return model; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }

    /** Identity of a device: the network it was seen on and the fingerprint of its MAC. */
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
