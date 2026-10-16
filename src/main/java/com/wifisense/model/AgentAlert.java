package com.wifisense.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** One alert raised on a home network: a short history for Mi Wi-Fi and the record used to not re-email the same thing. */
@Entity
@Table(name = "agent_alerts")
public class AgentAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_network_id", nullable = false, updatable = false)
    private HomeNetwork network;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertType type;

    @Column(nullable = false, length = 300)
    private String detail;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean emailed;

    protected AgentAlert() {
    }

    public AgentAlert(HomeNetwork network, AlertType type, String detail, boolean emailed) {
        this.network = network;
        this.type = type;
        this.detail = detail;
        this.emailed = emailed;
    }

    public Long getId() { return id; }
    public HomeNetwork getNetwork() { return network; }
    public AlertType getType() { return type; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isEmailed() { return emailed; }
}
