package com.wifisense.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Sensor of a team network: the WiFiSense agent installed on a computer connected to it. Only the SHA-256 of its key
 * is stored; the key travels once, to the installer.
 */
@Entity
@Table(name = "network_sensors")
public class NetworkSensor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "network_id", nullable = false, updatable = false)
    private Network network;

    @Column(name = "key_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)", updatable = false)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, length = 12, updatable = false)
    private String keyPrefix;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    /** While in the future, the agent reports every minute ("Which device is this?" on the page). */
    @Column(name = "identify_until")
    private Instant identifyUntil;

    @Column(name = "agent_version", length = 20)
    private String agentVersion;

    @Column(length = 20)
    private String platform;

    /** The Wi-Fi the sensor's computer was on in its last report; null if it could not be read. */
    @Column(name = "last_ssid", length = 64)
    private String lastSsid;

    protected NetworkSensor() {
    }

    public NetworkSensor(Network network, String keyHash, String keyPrefix) {
        this.network = network;
        this.keyHash = keyHash;
        this.keyPrefix = keyPrefix;
    }

    public Long getId() { return id; }
    public Network getNetwork() { return network; }
    public String getKeyHash() { return keyHash; }
    public String getKeyPrefix() { return keyPrefix; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getIdentifyUntil() { return identifyUntil; }
    public String getAgentVersion() { return agentVersion; }
    public String getPlatform() { return platform; }
    public String getLastSsid() { return lastSsid; }
}
