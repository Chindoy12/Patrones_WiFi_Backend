package com.wifisense.dto;

import java.time.Instant;

/**
 * The sensor of a team network as the panel shows it. {@code online}: it reported within the last minutes.
 * {@code pendingCodeExpiresAt}: a code was generated and not used yet (the code itself is shown only once).
 * {@code lastSsid}: the Wi-Fi the sensor's computer was on in its last report (null if it could not be read); when it
 * is not the network's, its reports are not stored and the panel says so.
 */
public record SensorStatus(boolean installed, boolean online, String keyPrefix, Instant installedAt,
                           Instant lastSeenAt, String agentVersion, String platform, Instant pendingCodeExpiresAt,
                           String lastSsid) {
}
