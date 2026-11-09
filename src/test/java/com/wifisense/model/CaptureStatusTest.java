package com.wifisense.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CaptureStatusTest {

    @Test
    void onlyAWaitingOrRunningCaptureIsActive() {
        assertThat(CaptureStatus.PENDING.active()).isTrue();
        assertThat(CaptureStatus.RUNNING.active()).isTrue();
        assertThat(CaptureStatus.COMPLETED.active()).isFalse();
        assertThat(CaptureStatus.FAILED.active()).isFalse();
        assertThat(CaptureStatus.CANCELLED.active()).isFalse();
    }

    @Test
    void aWaitingCaptureStartsFailsOrIsCancelledButNeverCompletesWithoutRunning() {
        assertThat(CaptureStatus.PENDING.canMoveTo(CaptureStatus.RUNNING)).isTrue();
        assertThat(CaptureStatus.PENDING.canMoveTo(CaptureStatus.FAILED)).isTrue();
        assertThat(CaptureStatus.PENDING.canMoveTo(CaptureStatus.CANCELLED)).isTrue();
        assertThat(CaptureStatus.PENDING.canMoveTo(CaptureStatus.COMPLETED)).isFalse();
    }

    @Test
    void aRunningCaptureEndsCompletedOrFailedAndIsNotCancelledFromTheServer() {
        assertThat(CaptureStatus.RUNNING.canMoveTo(CaptureStatus.COMPLETED)).isTrue();
        assertThat(CaptureStatus.RUNNING.canMoveTo(CaptureStatus.FAILED)).isTrue();
        assertThat(CaptureStatus.RUNNING.canMoveTo(CaptureStatus.CANCELLED)).isFalse();
    }

    @Test
    void aFinishedCaptureNeverChangesAgain() {
        for (CaptureStatus finished : new CaptureStatus[]{CaptureStatus.COMPLETED, CaptureStatus.FAILED,
                CaptureStatus.CANCELLED}) {
            for (CaptureStatus next : CaptureStatus.values()) {
                assertThat(finished.canMoveTo(next)).as(finished + " -> " + next).isFalse();
            }
        }
    }
}
