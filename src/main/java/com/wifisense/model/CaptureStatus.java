package com.wifisense.model;

/**
 * Life of a technical capture: PENDING (asked by an administrator, waiting for the sensor), RUNNING (the sensor
 * started), then COMPLETED, FAILED or CANCELLED (stopped before it started). A finished capture never changes again.
 */
public enum CaptureStatus {
    PENDING, RUNNING, COMPLETED, FAILED, CANCELLED;

    /** Waiting or running: a network has at most one such capture. */
    public boolean active() {
        return this == PENDING || this == RUNNING;
    }

    /**
     * The only moves a capture can make. A running capture is not cancelled from here: the sensor stops it and
     * delivers what it captured (COMPLETED with stop reason CANCELLED).
     */
    public boolean canMoveTo(CaptureStatus next) {
        return switch (this) {
            case PENDING -> next == RUNNING || next == FAILED || next == CANCELLED;
            case RUNNING -> next == COMPLETED || next == FAILED;
            case COMPLETED, FAILED, CANCELLED -> false;
        };
    }
}
