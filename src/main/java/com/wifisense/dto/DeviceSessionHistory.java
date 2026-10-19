package com.wifisense.dto;

import java.time.Instant;
import java.util.List;

/**
 * When a device was connected in the last days: each period (newest first), how many times it connected and for how
 * long in total. {@code endedAt} is null while it is still connected ({@code ongoing}).
 */
public record DeviceSessionHistory(String deviceId, int days, int connections, long totalMinutes,
                                   List<Period> periods) {

    public record Period(Instant connectedAt, Instant endedAt, boolean ongoing, long minutes) {
    }
}
