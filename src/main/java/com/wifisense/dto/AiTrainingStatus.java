package com.wifisense.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

/**
 * Training the anomaly model with real measurements: what data there is, how much it needs, and every training so far
 * (newest first). {@code canTrain}: there are enough measurements. {@code nextAutomatic}: when the weekly training
 * runs (null when it is turned off).
 */
public record AiTrainingStatus(Data available, int minimumSamples, boolean canTrain, Instant nextAutomatic,
                               List<Version> versions) {

    /** The real measurements that would be used now: how many, from how many networks, and their period. */
    public record Data(int samples, int teamSamples, int homeSamples, int networks, Instant from, Instant to) {
    }

    /**
     * One training. {@code metrics}: the new model and the model then in use measured on the same test data.
     * {@code decision}: PROMOTED (it replaced the model in use) or REJECTED (it was not better).
     */
    public record Version(Long id, String version, String algorithm, Instant trainedAt, String trainedBy, int samples,
                          int networks, Instant dataFrom, Instant dataTo, JsonNode metrics, String decision,
                          String reason, boolean active) {
    }
}
