package com.wifisense.service;

import com.wifisense.dto.AgentCaptureResult;
import com.wifisense.model.CaptureFailure;
import com.wifisense.model.CaptureStatus;
import com.wifisense.model.CaptureStopReason;
import com.wifisense.model.CaptureTool;
import com.wifisense.model.Network;
import com.wifisense.model.TrafficCapture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.wifisense.service.CaptureFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaptureRecorderTest {

    private final CaptureRecorder recorder = new CaptureRecorder();
    private final Network network = CaptureFixtures.network(4L);
    private final TrafficCapture capture = CaptureFixtures.running(network, 7L, NOW.minusSeconds(31));

    /** The fixture result with some fields changed. */
    private static AgentCaptureResult with(Integer packets, Long bytes, Integer broadcast,
                                           List<AgentCaptureResult.Protocol> protocols,
                                           List<AgentCaptureResult.Interval> intervals, Integer durationMs) {
        AgentCaptureResult r = CaptureFixtures.completed(7L);
        return new AgentCaptureResult(7L, "COMPLETED", null, r.stopReason(), r.captureTool(), r.interfaceName(),
                r.agentVersion(), durationMs, packets, bytes, r.droppedByTool(), 0, 0L, 0L, broadcast, 0,
                r.distinctLocalHosts(), r.distinctRemoteHosts(), protocols, List.of(), intervals);
    }

    @Test
    void aConsistentResultCompletesTheCaptureWithEveryStatistic() {
        recorder.apply(capture, CaptureFixtures.completed(7L), NOW);

        assertThat(capture.getStatus()).isEqualTo(CaptureStatus.COMPLETED);
        assertThat(capture.getStopReason()).isEqualTo(CaptureStopReason.DURATION);
        assertThat(capture.getCaptureTool()).isEqualTo(CaptureTool.PKTMON);
        assertThat(capture.getPacketCount()).isEqualTo(1200);
        assertThat(capture.getProtocols()).hasSize(4);
        assertThat(capture.getPorts()).hasSize(2);
        assertThat(capture.getIntervals()).hasSize(30);
        assertThat(capture.getBroadcastPackets()).isEqualTo(40);
    }

    @Test
    void aFailureKeepsTheReasonTheSensorFound() {
        recorder.apply(capture, CaptureFixtures.failed(7L, "NO_CONSENT"), NOW);

        assertThat(capture.getStatus()).isEqualTo(CaptureStatus.FAILED);
        assertThat(capture.getFailureReason()).isEqualTo(CaptureFailure.NO_CONSENT);
    }

    @Test
    void aFailureWithoutReasonIsACaptureError() {
        recorder.apply(capture, CaptureFixtures.failed(7L, null), NOW);

        assertThat(capture.getFailureReason()).isEqualTo(CaptureFailure.CAPTURE_ERROR);
    }

    @Test
    void anEmptyCaptureIsAValidResult() {
        recorder.apply(capture, with(0, 0L, 0, List.of(), List.of(), 30_000), NOW);

        assertThat(capture.getStatus()).isEqualTo(CaptureStatus.COMPLETED);
        assertThat(capture.getPacketCount()).isZero();
    }

    @Test
    void numbersOverTheLimitsTheAdministratorSetAreRefused() {
        assertThatThrownBy(() -> recorder.apply(capture, with(5001, 10L, 0, List.of(), List.of(), 30_000), NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("límite");
        assertThatThrownBy(() -> recorder.apply(capture, with(10, 30L << 20, 0, List.of(), List.of(), 30_000), NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bytes");
        assertThatThrownBy(() -> recorder.apply(capture, with(10, 10L, 0, List.of(), List.of(), 200_000), NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duró");
    }

    @Test
    void partsThatAddUpToMoreThanTheWholeAreRefused() {
        assertThatThrownBy(() -> recorder.apply(capture, with(10, 1000L, 11, List.of(), List.of(), 1000), NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("difusión");
        assertThatThrownBy(() -> recorder.apply(capture, with(10, 1000L, 0,
                List.of(new AgentCaptureResult.Protocol("UDP", 11, 100L)), List.of(), 1000), NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("protocolos");
        assertThatThrownBy(() -> recorder.apply(capture, with(10, 1000L, 0, List.of(),
                List.of(new AgentCaptureResult.Interval(0, 6, 100L), new AgentCaptureResult.Interval(1, 6, 100L)),
                1000), NOW)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("segundos");
        assertThatThrownBy(() -> recorder.apply(capture, with(0, 5L, 0, List.of(), List.of(), 1000), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void repeatedProtocolsOrSecondsAndTrafficAfterTheEndAreRefused() {
        assertThatThrownBy(() -> recorder.apply(capture, with(10, 1000L, 0,
                List.of(new AgentCaptureResult.Protocol("DNS", 2, 100L), new AgentCaptureResult.Protocol("DNS", 2, 100L)),
                List.of(), 1000), NOW)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dos veces");
        assertThatThrownBy(() -> recorder.apply(capture, with(10, 1000L, 0, List.of(),
                List.of(new AgentCaptureResult.Interval(3, 1, 10L), new AgentCaptureResult.Interval(3, 1, 10L)),
                1000), NOW)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dos veces");
        assertThatThrownBy(() -> recorder.apply(capture, with(10, 1000L, 0, List.of(),
                List.of(new AgentCaptureResult.Interval(200, 1, 10L)), 1000), NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("después del final");
    }

    @Test
    void aCompletedResultMustSayWhyItStoppedAndWithWhichTool() {
        AgentCaptureResult r = CaptureFixtures.completed(7L);
        AgentCaptureResult noStop = new AgentCaptureResult(7L, "COMPLETED", null, null, "NPCAP", null, "2.0.0", 1000,
                1, 60L, null, null, null, null, null, null, null, null, null, null, null);
        AgentCaptureResult noTool = new AgentCaptureResult(7L, "COMPLETED", null, "DURATION", null, null, "2.0.0",
                1000, 1, 60L, null, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> recorder.apply(capture, noStop, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recorder.apply(capture, noTool, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThat(r.protocolsOrEmpty()).isNotEmpty();
        assertThat(capture.getStatus()).isEqualTo(CaptureStatus.RUNNING);
    }
}
