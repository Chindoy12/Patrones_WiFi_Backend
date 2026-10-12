package com.wifisense.model;

import com.wifisense.analysis.AiAnalysisResponse;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "ai_predictions")
public class AiPrediction {

    public enum Severity { NONE, LOW, MEDIUM, HIGH }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_result_id", nullable = false, unique = true)
    private AnalysisResult analysisResult;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "network_id", nullable = false)
    private Network network;

    @Column(name = "anomaly_detected", nullable = false)
    private boolean anomalyDetected;

    @Column(name = "anomaly_score", nullable = false)
    private double anomalyScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    /** Null only for predictions saved before the AI diagnosed causes (migration V15). */
    @Enumerated(EnumType.STRING)
    @Column(name = "anomaly_type", length = 20)
    private AnomalyType anomalyType;

    @Column(nullable = false, length = 300)
    private String message;

    @Column(length = 1500)
    private String recommendation;

    @Column(name = "recommendation_source", nullable = false, length = 10)
    private String recommendationSource = "RULES";

    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion;

    @Column(name = "simulated_data", nullable = false)
    private boolean simulatedData;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiPrediction() {
    }

    public static AiPrediction from(AnalysisResult result, AiAnalysisResponse response) {
        AiPrediction prediction = new AiPrediction();
        prediction.analysisResult = result;
        prediction.network = result.getNetwork();
        prediction.anomalyDetected = response.anomalyDetected();
        prediction.anomalyScore = response.anomalyScore();
        prediction.severity = response.severity();
        prediction.anomalyType = AnomalyType.fromAi(response.anomalyType(), response.anomalyDetected());
        prediction.message = truncate(response.message(), 300);
        prediction.recommendation = response.recommendation() == null ? null : truncate(response.recommendation(), 1500);
        prediction.recommendationSource = "CLAUDE".equals(response.recommendationSource()) ? "CLAUDE" : "RULES";
        prediction.modelVersion = response.modelVersion();
        prediction.simulatedData = response.simulatedData();
        return prediction;
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }

    public Long getId() { return id; }
    public AnalysisResult getAnalysisResult() { return analysisResult; }
    public Network getNetwork() { return network; }
    public boolean isAnomalyDetected() { return anomalyDetected; }
    public double getAnomalyScore() { return anomalyScore; }
    public Severity getSeverity() { return severity; }
    public AnomalyType getAnomalyType() { return anomalyType; }
    public String getMessage() { return message; }
    public String getRecommendation() { return recommendation; }
    public String getRecommendationSource() { return recommendationSource; }
    public String getModelVersion() { return modelVersion; }
    public boolean isSimulatedData() { return simulatedData; }
    public Instant getCreatedAt() { return createdAt; }
}
