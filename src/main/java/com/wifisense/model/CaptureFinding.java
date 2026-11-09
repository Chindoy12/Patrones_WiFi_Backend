package com.wifisense.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One conclusion of the analysis of a capture (a row of traffic_capture_findings). An observation is a count and has
 * no confidence; an inference always has one, between 0 and 1. The constructors enforce it, like the table does.
 */
@Embeddable
public class CaptureFinding {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private FindingCategory category;

    @Column(nullable = false, length = 32)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private FindingSeverity severity;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 400)
    private String evidence;

    @Column(length = 400)
    private String recommendation;

    @Column(precision = 3, scale = 2)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private FindingSource source;

    protected CaptureFinding() {
    }

    private CaptureFinding(FindingCategory category, String code, FindingSeverity severity, String title,
                           String evidence, String recommendation, BigDecimal confidence, FindingSource source) {
        if (code == null || !code.matches("^[A-Z][A-Z0-9_]{2,31}$")) {
            throw new IllegalArgumentException("Código de hallazgo inválido: " + code);
        }
        this.category = category;
        this.code = code;
        this.severity = severity;
        this.title = clip(title, 120);
        this.evidence = clip(evidence, 400);
        this.recommendation = recommendation == null || recommendation.isBlank() ? null : clip(recommendation, 400);
        this.confidence = confidence;
        this.source = source;
    }

    /** A fact computed from the captured packets. */
    public static CaptureFinding observed(String code, FindingSeverity severity, String title, String evidence,
                                          String recommendation, FindingSource source) {
        return new CaptureFinding(FindingCategory.OBSERVED, code, severity, title, evidence, recommendation, null,
                source);
    }

    /** A possible explanation, with how sure it is (clamped to [0, 1], two decimals). */
    public static CaptureFinding inferred(String code, FindingSeverity severity, String title, String evidence,
                                          String recommendation, double confidence, FindingSource source) {
        double bounded = Double.isNaN(confidence) ? 0 : Math.max(0, Math.min(1, confidence));
        return new CaptureFinding(FindingCategory.INFERRED, code, severity, title, evidence, recommendation,
                BigDecimal.valueOf(bounded).setScale(2, RoundingMode.HALF_UP), source);
    }

    private static String clip(String text, int max) {
        String value = text == null || text.isBlank() ? "—" : text.strip();
        return value.length() > max ? value.substring(0, max - 1) + "…" : value;
    }

    public FindingCategory getCategory() { return category; }
    public String getCode() { return code; }
    public FindingSeverity getSeverity() { return severity; }
    public String getTitle() { return title; }
    public String getEvidence() { return evidence; }
    public String getRecommendation() { return recommendation; }
    public BigDecimal getConfidence() { return confidence; }
    public FindingSource getSource() { return source; }
}
