package com.wifisense.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Key of the agent of one home network. Only its SHA-256 is stored; the key is shown to the user once. */
@Entity
@Table(name = "agent_keys")
public class AgentKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_network_id", nullable = false, updatable = false)
    private HomeNetwork network;

    @Column(name = "key_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, length = 12)
    private String keyPrefix;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    /** While in the future, the agent reports every minute ("Which device is this?" on the page). */
    @Column(name = "identify_until")
    private Instant identifyUntil;

    protected AgentKey() {
    }

    public AgentKey(HomeNetwork network, String keyHash, String keyPrefix) {
        this.network = network;
        this.keyHash = keyHash;
        this.keyPrefix = keyPrefix;
    }

    public void seen(Instant at) {
        this.lastSeenAt = at;
    }

    public Long getId() { return id; }
    public HomeNetwork getNetwork() { return network; }
    public String getKeyHash() { return keyHash; }
    public String getKeyPrefix() { return keyPrefix; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getIdentifyUntil() { return identifyUntil; }
}
