package com.wifisense.analysis;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

/** Abstraction over the AI service so the analysis does not depend on HTTP details (DIP). */
public interface AiAnalysisClient {

    AiAnalysisResponse detectAnomalies(AiAnalysisRequest request);

    /** Algorithm, version, features, threshold and training data of the production model. */
    JsonNode modelInfo();

    /** Comparison of algorithms; empty while the AI service is still computing it after a restart. */
    Optional<JsonNode> modelEvaluation();

    /** 0-100 quality of one connection test or measurement, with activities and tips. */
    JsonNode qualityScore(AiQualityRequest request);

    /** True when the AI service answers its health check; never throws. */
    boolean isHealthy();
}
