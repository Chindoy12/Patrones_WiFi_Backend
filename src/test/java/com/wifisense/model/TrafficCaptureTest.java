package com.wifisense.model;

import com.wifisense.service.CaptureFixtures;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.wifisense.service.CaptureFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrafficCaptureTest {

    private final Network network = CaptureFixtures.network(4L);

    private static TrafficCapture.CaptureTotals totals(int packets) {
        return new TrafficCapture.CaptureTotals(packets, packets * 700L, 0, 0, 1000L, 500L, 2, 3, 4, 10);
    }

    private static final TrafficCapture.CaptureDetails DETAILS =
            new TrafficCapture.CaptureDetails(CaptureTool.NPCAP, "Wi-Fi", "2.0.0", 30_000);

    @Test
    void aWaitingCaptureThatIsCancelledEndsAtOnce() {
        TrafficCapture capture = CaptureFixtures.pending(network, 1L, NOW);

        capture.requestCancel(NOW.plusSeconds(10));

        assertThat(capture.getStatus()).isEqualTo(CaptureStatus.CANCELLED);
        assertThat(capture.getFinishedAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(capture.cancelRequested()).isTrue();
    }

    @Test
    void aRunningCaptureThatIsCancelledKeepsRunningUntilTheSensorDeliversWhatItCaptured() {
        TrafficCapture capture = CaptureFixtures.running(network, 1L, NOW);

        capture.requestCancel(NOW.plusSeconds(5));
        assertThat(capture.getStatus()).isEqualTo(CaptureStatus.RUNNING);
        assertThat(capture.cancelRequested()).isTrue();

        capture.complete(NOW.plusSeconds(8), CaptureStopReason.CANCELLED, DETAILS, totals(50), List.of(), List.of(),
                List.of());
        assertThat(capture.getStatus()).isEqualTo(CaptureStatus.COMPLETED);
        assertThat(capture.getStopReason()).isEqualTo(CaptureStopReason.CANCELLED);
        assertThat(capture.getPacketCount()).isEqualTo(50);
    }

    @Test
    void aFinishedCaptureCannotBeCancelledCompletedOrFailedAgain() {
        TrafficCapture capture = CaptureFixtures.running(network, 1L, NOW);
        capture.fail(NOW.plusSeconds(3), CaptureFailure.PERMISSION_DENIED, DETAILS);

        assertThatThrownBy(() -> capture.requestCancel(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> capture.complete(NOW, CaptureStopReason.DURATION, DETAILS, totals(1), List.of(),
                List.of(), List.of())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> capture.fail(NOW, CaptureFailure.CAPTURE_ERROR, null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(capture.getFailureReason()).isEqualTo(CaptureFailure.PERMISSION_DENIED);
    }

    @Test
    void aCaptureCannotCompleteWithoutHavingStarted() {
        TrafficCapture capture = CaptureFixtures.pending(network, 1L, NOW);

        assertThatThrownBy(() -> capture.complete(NOW, CaptureStopReason.DURATION, DETAILS, totals(1), List.of(),
                List.of(), List.of())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aClockThatWentBackNeverPutsAnEventBeforeTheRequest() {
        TrafficCapture capture = CaptureFixtures.pending(network, 1L, NOW);
        Instant earlier = NOW.minusSeconds(60);

        capture.start(earlier);
        capture.fail(earlier, CaptureFailure.CAPTURE_ERROR, null);

        assertThat(capture.getStartedAt()).isEqualTo(capture.getRequestedAt());
        assertThat(capture.getFinishedAt()).isEqualTo(capture.getRequestedAt());
    }

    @Test
    void onlyACompletedCaptureIsAnalysedAndANewAnalysisReplacesTheOldOne() {
        TrafficCapture capture = CaptureFixtures.running(network, 1L, NOW);
        CaptureFinding fact = CaptureFinding.observed("TOP_PROTOCOL", FindingSeverity.INFO, "HTTPS", "x", null,
                FindingSource.RULES);
        assertThatThrownBy(() -> capture.analyzed(FindingSource.RULES, List.of(fact)))
                .isInstanceOf(IllegalStateException.class);

        capture.complete(NOW.plusSeconds(30), CaptureStopReason.DURATION, DETAILS, totals(10), List.of(), List.of(),
                List.of());
        capture.analyzed(FindingSource.RULES, List.of(fact, fact));
        capture.analyzed(FindingSource.AI, List.of(fact));

        assertThat(capture.getFindings()).hasSize(1);
        assertThat(capture.getAnalyzedWith()).isEqualTo(FindingSource.AI);
    }
}
