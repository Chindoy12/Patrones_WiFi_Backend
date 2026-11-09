package com.wifisense.model;

/**
 * Whether a finding is a fact measured in the capture (OBSERVED, no confidence: it is a count) or a possible
 * explanation (INFERRED, always with its confidence and the evidence it rests on).
 */
public enum FindingCategory {
    OBSERVED, INFERRED
}
