package com.wifisense.service;

import com.wifisense.dto.AgentLiveAck;
import com.wifisense.dto.AgentLiveSample;
import com.wifisense.dto.AgentLiveTask;
import com.wifisense.dto.LiveView;
import com.wifisense.service.LiveMonitor.Kind;
import com.wifisense.service.LiveMonitor.Target;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiveMonitorTest {

    private static final Target HOME = new Target(Kind.HOME, 7L);
    private static final Target TEAM = new Target(Kind.TEAM, 7L);

    private final TestClock clock = new TestClock(Instant.parse("2026-11-11T15:00:00Z"));
    private final LiveMonitor monitor = new LiveMonitor(clock);

    private static AgentLiveSample sample(long downloadBps, Double internetMs) {
        return new AgentLiveSample(2000, downloadBps, 250_000L, internetMs, internetMs == null ? true : null, 3.0,
                null, 70, 144.4, 72.2);
    }

    /** The page stays open for {@code seconds}: it asks every 2 seconds, as the real page does. */
    private LiveView watchFor(Target target, int seconds) {
        LiveView view = monitor.watch(target, 0);
        for (int i = 0; i < seconds / 2; i++) {
            clock.advance(Duration.ofSeconds(2));
            view = monitor.watch(target, 0);
        }
        return view;
    }

    /** The agent sends a sample, then 2 seconds go by. */
    private AgentLiveAck send(Target target, long downloadBps, Double internetMs) {
        AgentLiveAck ack = monitor.receive(target, sample(downloadBps, internetMs));
        clock.advance(Duration.ofSeconds(2));
        return ack;
    }

    @Test
    void whenNobodyWatchesTheAgentHasNothingToDoAndItsSamplesAreNotKept() {
        assertThat(monitor.agentAsks(HOME)).isEmpty();
        assertThat(monitor.receive(HOME, sample(1_000_000, 30.0))).isEqualTo(new AgentLiveAck(false));
        assertThat(monitor.channelCount()).isZero();  // an agent asking never creates anything
    }

    @Test
    void whileThePageWatchesTheAgentMeasuresEvery2SecondsAndThePageGetsTheSamples() {
        monitor.watch(HOME, 0);

        assertThat(monitor.agentAsks(HOME)).contains(new AgentLiveTask(true, 2, 600));
        assertThat(send(HOME, 4_000_000, 30.0)).isEqualTo(new AgentLiveAck(true));
        send(HOME, 12_340_000, 35.5);

        LiveView view = monitor.watch(HOME, 0);
        assertThat(view.state()).isEqualTo("LIVE");
        assertThat(view.lastSeq()).isEqualTo(2);
        assertThat(view.points()).extracting(LiveView.Point::downloadMbps).containsExactly(4.0, 12.34);
        assertThat(view.points().get(0).uploadMbps()).isEqualTo(0.25);
        assertThat(view.points().get(1).internetMs()).isEqualTo(35.5);
        assertThat(view.points().get(0).signalPercent()).isEqualTo(70);
    }

    @Test
    void thePageOnlyGetsTheSamplesItDoesNotHaveYet() {
        monitor.watch(HOME, 0);
        send(HOME, 1_000_000, 30.0);
        send(HOME, 2_000_000, 30.0);
        long seen = monitor.watch(HOME, 0).lastSeq();
        send(HOME, 3_000_000, 30.0);

        LiveView view = monitor.watch(HOME, seen);

        assertThat(view.points()).extracting(LiveView.Point::seq).containsExactly(3L);
        assertThat(view.summary().samples()).isEqualTo(3);  // the summary still covers the last minute
    }

    @Test
    void thirtySecondsAfterThePageStopsAskingTheAgentIsToldToStop() {
        monitor.watch(HOME, 0);
        clock.advance(Duration.ofSeconds(29));
        assertThat(monitor.receive(HOME, sample(1, 30.0)).keepGoing()).isTrue();

        clock.advance(Duration.ofSeconds(2));

        assertThat(monitor.receive(HOME, sample(1, 30.0)).keepGoing()).isFalse();
        assertThat(monitor.agentAsks(HOME)).isEmpty();
    }

    @Test
    void samplesCloserThanASecondAreDroppedSoAnAgentCannotFloodTheMemory() {
        monitor.watch(HOME, 0);
        monitor.receive(HOME, sample(1_000_000, 30.0));
        clock.advance(Duration.ofMillis(500));
        assertThat(monitor.receive(HOME, sample(9_000_000, 30.0)).keepGoing()).isTrue();

        assertThat(monitor.watch(HOME, 0).points()).hasSize(1);
    }

    @Test
    void onlyTheLastFiveMinutesAreKept() {
        monitor.watch(HOME, 0);
        for (int i = 0; i < 200; i++) {  // 400 seconds of samples, watching all the time
            if (i % 10 == 0) {
                monitor.watch(HOME, 0);
            }
            send(HOME, i * 1000L, 30.0);
        }

        LiveView view = monitor.watch(HOME, 0);

        assertThat(view.points()).hasSize(LiveMonitor.MAX_POINTS);
        assertThat(view.points().get(0).at()).isAfterOrEqualTo(clock.instant().minus(Duration.ofMinutes(5)));
        assertThat(view.summary().samples()).isEqualTo(30);  // the summary is the last minute
    }

    @Test
    void theStateSaysWhetherTheAgentAnswered() {
        assertThat(monitor.watch(HOME, 0).state()).isEqualTo("WAITING");

        assertThat(watchFor(HOME, 44).state()).isEqualTo("WAITING");
        // 46 seconds and the agent never asked: off, without internet or older than 2.1.0
        assertThat(watchFor(HOME, 2).state()).isEqualTo("NO_AGENT");

        monitor.agentAsks(HOME);
        assertThat(monitor.watch(HOME, 0).state()).isEqualTo("WAITING");
        send(HOME, 1, 30.0);
        assertThat(monitor.watch(HOME, 0).state()).isEqualTo("LIVE");
    }

    @Test
    void whenTheSamplesStopTheStateGoesBackToWaitingAndThenToNoAgent() {
        monitor.watch(HOME, 0);
        monitor.agentAsks(HOME);
        send(HOME, 1, 30.0);

        assertThat(watchFor(HOME, 10).state()).isEqualTo("WAITING");
        // nothing for 50 seconds while the page stays open: the computer was turned off
        assertThat(watchFor(HOME, 40).state()).isEqualTo("NO_AGENT");
    }

    @Test
    void anAgentThatAsksButNeverSendsIsReportedAfter90Seconds() {
        for (int i = 0; i < 2; i++) {
            watchFor(HOME, 30);
            monitor.agentAsks(HOME);
        }
        assertThat(watchFor(HOME, 28).state()).isEqualTo("WAITING");

        assertThat(watchFor(HOME, 2).state()).isEqualTo("NO_AGENT");  // 90 seconds without a single sample
    }

    @Test
    void aHomeNetworkAndATeamNetworkWithTheSameNumberAreDifferent() {
        monitor.watch(HOME, 0);

        assertThat(monitor.agentAsks(TEAM)).isEmpty();
        assertThat(monitor.receive(TEAM, sample(1, 30.0)).keepGoing()).isFalse();
    }

    @Test
    void networksNobodyWatchedForFiveMinutesAreForgotten() {
        monitor.watch(HOME, 0);
        monitor.watch(TEAM, 0);
        clock.advance(Duration.ofMinutes(5));
        monitor.watch(TEAM, 0);
        clock.advance(Duration.ofSeconds(31));

        monitor.forgetIdle(clock.instant());

        assertThat(monitor.channelCount()).isEqualTo(1);
        assertThat(monitor.agentAsks(HOME)).isEmpty();
    }

    @Test
    void thereIsALimitOfNetworksWatchedAtOnce() {
        for (long id = 1; id <= LiveMonitor.MAX_CHANNELS; id++) {
            monitor.watch(new Target(Kind.HOME, id), 0);
        }

        assertThatThrownBy(() -> monitor.watch(new Target(Kind.TEAM, 1L), 0))
                .isInstanceOf(IllegalStateException.class);
        monitor.watch(new Target(Kind.HOME, 1L), 0);  // one already open keeps working
    }
}
