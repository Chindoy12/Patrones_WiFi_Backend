package com.wifisense.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * What the team sees about the AI: the production model and how it compares with other algorithms. evaluation is
 * null while the AI service is still computing it (a few seconds after it restarts).
 */
public record AiModelOverview(JsonNode model, JsonNode evaluation, boolean evaluationReady) {
}
