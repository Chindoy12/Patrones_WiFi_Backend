package com.wifisense.analysis;

/**
 * Contract of POST /api/v1/quality/score in WiFiSense-ai. Only latency and jitter are required; any other metric
 * that was not measured travels as null and the AI leaves it out of the grade.
 */
public record AiQualityRequest(double latencyMs, double jitterMs, Double packetLossPct, Double downloadMbps,
                               Double uploadMbps, Double contractedMbps, Double signalDbm) {
}
