package com.wifisense.dto;

/** Which email alerts the end user wants. */
public record AlertPreferenceResponse(boolean newDevice, boolean security, boolean anomaly) {
}
