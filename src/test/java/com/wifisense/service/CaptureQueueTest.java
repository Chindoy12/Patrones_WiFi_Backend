package com.wifisense.service;

import com.wifisense.model.CaptureFailure;
import com.wifisense.model.CaptureStatus;
import com.wifisense.model.Network;
import com.wifisense.model.TrafficCapture;
import com.wifisense.repository.NetworkRepository;
import com.wifisense.repository.TrafficCaptureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.wifisense.service.CaptureFixtures.LIMITS;
import static com.wifisense.service.CaptureFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CaptureQueueTest {

    private final TrafficCaptureRepository captures = mock(TrafficCaptureRepository.class);
    private final NetworkRepository networks = mock(NetworkRepository.class);
    private final CaptureQueue queue = new CaptureQueue(captures, networks, CaptureFixtures.PROPERTIES);
    private final Network network = CaptureFixtures.network(4L);

    @BeforeEach
    void setUp() {
        when(networks.getReferenceById(4L)).thenReturn(network);
        when(captures.saveAndFlush(any(TrafficCapture.class))).thenAnswer(call -> call.getArgument(0));
    }

    private void active(TrafficCapture capture) {
        when(captures.findFirstByNetworkIdAndStatusIn(eq(4L), any())).thenReturn(Optional.of(capture));
    }

    @Test
    void aNetworkHasAtMostOneCaptureWaitingOrRunning() {
        active(CaptureFixtures.pending(network, 1L, NOW.minusSeconds(30)));

        assertThatThrownBy(() -> queue.enqueue(4L, 1L, "Otra captura al mismo tiempo", LIMITS, NOW))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("en curso");
    }

    @Test
    void aNewCaptureWaitsForTheSensorWithItsPurposeAndLimits() {
        when(captures.findFirstByNetworkIdAndStatusIn(eq(4L), any())).thenReturn(Optional.empty());

        TrafficCapture capture = queue.enqueue(4L, 9L, "Revisar la lentitud de la sala", LIMITS, NOW);

        assertThat(capture.getStatus()).isEqualTo(CaptureStatus.PENDING);
        assertThat(capture.getRequestedBy()).isEqualTo(9L);
        assertThat(capture.getMaxSeconds()).isEqualTo(30);
        assertThat(capture.getMaxPackets()).isEqualTo(5000);
    }

    @Test
    void theSensorTakesTheWaitingCaptureWhichStartsRunning() {
        TrafficCapture waiting = CaptureFixtures.pending(network, 1L, NOW.minusSeconds(30));
        active(waiting);

        assertThat(queue.take(4L, NOW)).contains(waiting);
        assertThat(waiting.getStatus()).isEqualTo(CaptureStatus.RUNNING);
        assertThat(waiting.getStartedAt()).isEqualTo(NOW);
    }

    @Test
    void aRunningCaptureIsNotHandedOutAgain() {
        active(CaptureFixtures.running(network, 1L, NOW.minusSeconds(10)));

        assertThat(queue.take(4L, NOW)).isEmpty();
    }

    @Test
    void aCaptureTheSensorNeverPickedUpFailsAfterTheStartTimeout() {
        TrafficCapture waiting = CaptureFixtures.pending(network, 1L, NOW.minusSeconds(181));
        active(waiting);

        assertThat(queue.active(4L, NOW)).isEmpty();
        assertThat(waiting.getStatus()).isEqualTo(CaptureStatus.FAILED);
        assertThat(waiting.getFailureReason()).isEqualTo(CaptureFailure.AGENT_NO_RESPONSE);
        verify(captures).saveAndFlush(waiting);
    }

    @Test
    void aRunningCaptureHasItsDurationPlusTheGraceToDeliver() {
        TrafficCapture running = CaptureFixtures.running(network, 1L, NOW.minusSeconds(30 + 89));
        active(running);
        assertThat(queue.active(4L, NOW)).contains(running);

        TrafficCapture late = CaptureFixtures.running(network, 2L, NOW.minusSeconds(30 + 90));
        active(late);
        assertThat(queue.active(4L, NOW)).isEmpty();
        assertThat(late.getFailureReason()).isEqualTo(CaptureFailure.AGENT_NO_RESPONSE);
    }
}
