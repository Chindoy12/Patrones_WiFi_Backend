package com.wifisense.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** A network an end user registers for themself; it never mixes with the university networks. */
@Entity
@Table(name = "home_networks")
public class HomeNetwork {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private User owner;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(length = 60)
    private String provider;

    @Column(name = "contracted_mbps")
    private Integer contractedMbps;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HomeNetwork() {
    }

    public HomeNetwork(User owner, String name, String provider, Integer contractedMbps) {
        this.owner = owner;
        update(name, provider, contractedMbps);
    }

    public void update(String name, String provider, Integer contractedMbps) {
        this.name = name;
        this.provider = provider;
        this.contractedMbps = contractedMbps;
    }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public String getName() { return name; }
    public String getProvider() { return provider; }
    public Integer getContractedMbps() { return contractedMbps; }
    public Instant getCreatedAt() { return createdAt; }
}
