package com.wifisense.model;

import jakarta.persistence.*;

import java.time.Instant;

/** Short code shown in Mi Wi-Fi to install the agent; only its SHA-256 is stored and it works once. */
@Entity
@Table(name = "agent_pairing_codes")
public class AgentPairingCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_network_id", nullable = false, updatable = false)
    private HomeNetwork network;

    @Column(name = "code_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String codeHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    protected AgentPairingCode() {
    }

    public AgentPairingCode(HomeNetwork network, String codeHash, Instant createdAt, Instant expiresAt) {
        this.network = network;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public HomeNetwork getNetwork() { return network; }
    public String getCodeHash() { return codeHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
