package com.wifisense.dto;

import java.time.Instant;

/**
 * "Which device is this?": until {@code identifyUntil} the agent reports every {@code reportSeconds} seconds, so the
 * page sees within about a minute which device disappeared (when the user turns its Wi-Fi off) and which came back.
 * The agent switches on its next normal report, so it can take up to five minutes to start.
 */
public record IdentifyModeResponse(Instant identifyUntil, int reportSeconds) {
}
