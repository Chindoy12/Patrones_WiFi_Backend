package com.wifisense.analysis;

import com.wifisense.model.AiPrediction;

import java.util.List;
import java.util.Map;

public record AiAnalysisResponse(
        boolean anomalyDetected,
        double anomalyScore,
        AiPrediction.Severity severity,
        String anomalyType,
        String diagnosis,
        String message,
        String recommendation,
        String recommendationSource,
        List<String> contributingFeatures,
        Map<String, Double> featureDeviations,
        int anomalousSamples,
        int sampleSize,
        List<String> imputedFeatures,
        String modelVersion,
        boolean simulatedData) {
}
