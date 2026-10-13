package com.wifisense.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** One connection test an end user ran on one of their networks, with the grade the AI gave it. */
@Entity
@Table(name = "connection_tests")
public class ConnectionTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_network_id", nullable = false)
    private HomeNetwork network;

    @Column(name = "latency_ms", nullable = false)
    private double latencyMs;

    @Column(name = "jitter_ms", nullable = false)
    private double jitterMs;

    @Column(name = "download_mbps", nullable = false)
    private double downloadMbps;

    @Column(name = "upload_mbps", nullable = false)
    private double uploadMbps;

    /** Null when the AI service could not grade the test. */
    @Column(name = "quality_score")
    private Integer qualityScore;

    @Column(name = "quality_grade", length = 10)
    private String qualityGrade;

    /** The AI answer exactly as the page showed it (metrics, activities, tips). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quality_details")
    private String qualityDetails;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ConnectionTest() {
    }

    public ConnectionTest(HomeNetwork network, double latencyMs, double jitterMs, double downloadMbps,
                          double uploadMbps) {
        this.network = network;
        this.latencyMs = latencyMs;
        this.jitterMs = jitterMs;
        this.downloadMbps = downloadMbps;
        this.uploadMbps = uploadMbps;
    }

    public void grade(int score, String grade, String details) {
        this.qualityScore = score;
        this.qualityGrade = grade;
        this.qualityDetails = details;
    }

    public Long getId() { return id; }
    public HomeNetwork getNetwork() { return network; }
    public double getLatencyMs() { return latencyMs; }
    public double getJitterMs() { return jitterMs; }
    public double getDownloadMbps() { return downloadMbps; }
    public double getUploadMbps() { return uploadMbps; }
    public Integer getQualityScore() { return qualityScore; }
    public String getQualityGrade() { return qualityGrade; }
    public String getQualityDetails() { return qualityDetails; }
    public Instant getCreatedAt() { return createdAt; }
}
