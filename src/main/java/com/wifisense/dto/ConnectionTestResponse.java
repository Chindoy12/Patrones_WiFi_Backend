package com.wifisense.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/** quality is null when the AI service could not grade the test; the measured values are kept anyway. */
public record ConnectionTestResponse(Long id, Long networkId, double latencyMs, double jitterMs, double downloadMbps,
                                     double uploadMbps, Integer qualityScore, String qualityGrade, JsonNode quality,
                                     Instant createdAt) {
}
