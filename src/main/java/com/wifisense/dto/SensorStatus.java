package com.wifisense.dto;

import java.time.Instant;

/**
 * The sensor of a team network as the panel shows it. {@code online}: it reported within the last minutes.
 * {@code pendingCodeExpiresAt}: a code was generated and not used yet (the code itself is shown only once).
 */
public record SensorStatus(boolean installed, boolean online, String keyPrefix, Instant installedAt,
                           Instant lastSeenAt, String agentVersion, String platform, Instant pendingCodeExpiresAt) {
}
