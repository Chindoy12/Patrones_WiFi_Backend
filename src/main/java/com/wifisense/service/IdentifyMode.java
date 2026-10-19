package com.wifisense.service;

import java.time.Duration;
import java.time.Instant;

/** How long the agent reports every minute after the user starts "Which device is this?", and how often. */
public final class IdentifyMode {

    public static final Duration LENGTH = Duration.ofMinutes(10);
    public static final int REPORT_SECONDS = 60;

    private IdentifyMode() {
    }

    /** The interval to ask the agent for, or null (its usual one) when the mode is not active. */
    public static Integer nextReportSeconds(Instant identifyUntil, Instant now) {
        return active(identifyUntil, now) ? REPORT_SECONDS : null;
    }

    public static boolean active(Instant identifyUntil, Instant now) {
        return identifyUntil != null && identifyUntil.isAfter(now);
    }

    /** The end of the mode while it is active, null otherwise (what the page shows). */
    public static Instant visible(Instant identifyUntil, Instant now) {
        return active(identifyUntil, now) ? identifyUntil : null;
    }
}
