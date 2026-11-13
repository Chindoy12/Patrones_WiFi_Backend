package com.wifisense.service;

import com.wifisense.dto.AgentLiveAck;
import com.wifisense.dto.AgentLiveSample;
import com.wifisense.dto.AgentLiveTask;
import com.wifisense.dto.LiveView;
import com.wifisense.dto.LiveView.Point;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * «En vivo» (Avance 26): the live samples of each network, kept only in memory.
 * <p>
 * How it works: while a page has «En vivo» open it asks every 2 seconds ({@link #watch}), which keeps the network
 * "watched" for the next 30 seconds. Between reports the agent asks every 30 seconds whether someone is watching
 * ({@link #agentAsks}); if so, it measures every 2 seconds and sends each sample ({@link #receive}), and the answer
 * tells it to stop once nobody watches. Nothing is written to the database: live samples are worth something only
 * for a few minutes (the 5-minute reports keep the history), so there is no new table and no migration.
 * <p>
 * Limits: the last 5 minutes per network (150 samples), samples closer than 0.8 s are dropped (a misbehaving agent
 * cannot flood the memory) and at most 2000 networks watched at once. A network nobody watched for 5 minutes is
 * forgotten. The memory belongs to one instance of the backend (Railway runs one); after a redeploy the live view
 * just starts again.
 */
@Component
public class LiveMonitor {

    public static final int SAMPLE_SECONDS = 2;
    public static final int SESSION_SECONDS = 600;
    static final Duration WATCH_GRACE = Duration.ofSeconds(30);
    static final Duration KEEP = Duration.ofMinutes(5);
    static final Duration FRESH = Duration.ofSeconds(8);
    static final Duration AGENT_SILENT = Duration.ofSeconds(45);
    static final Duration NO_SAMPLES = Duration.ofSeconds(90);
    static final Duration SUMMARY_WINDOW = Duration.ofSeconds(60);
    static final Duration MIN_GAP = Duration.ofMillis(800);
    static final int MAX_POINTS = 150;
    static final int MAX_CHANNELS = 2000;

    public enum Kind { HOME, TEAM }

    /** A network of Mi Wi-Fi (HOME) or of the team (TEAM): their ids come from different tables. */
    public record Target(Kind kind, Long networkId) {
    }

    private final Map<Target, Channel> channels = new ConcurrentHashMap<>();
    private final Clock clock;

    public LiveMonitor(Clock clock) {
        this.clock = clock;
    }

    /** The page asks: someone is watching for the next 30 seconds. Returns the samples after {@code after}. */
    public LiveView watch(Target target, long after) {
        Instant now = clock.instant();
        Channel channel = channels.computeIfAbsent(target, key -> {
            if (channels.size() >= MAX_CHANNELS) {
                throw new IllegalStateException("Hay demasiadas mediciones en vivo abiertas. Intenta en un minuto.");
            }
            return new Channel();
        });
        synchronized (channel) {
            if (!channel.watched(now)) {
                channel.watchingSince = now;
            }
            channel.watchUntil = now.plus(WATCH_GRACE);
            return channel.view(after, now);
        }
    }

    /** The agent asks between reports whether someone is watching. A network nobody opened has no channel. */
    public Optional<AgentLiveTask> agentAsks(Target target) {
        Channel channel = channels.get(target);
        if (channel == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        synchronized (channel) {
            channel.agentAskedAt = now;
            return channel.watched(now)
                    ? Optional.of(new AgentLiveTask(true, SAMPLE_SECONDS, SESSION_SECONDS))
                    : Optional.empty();
        }
    }

    /** A sample of the agent. When nobody is watching it is not kept and the agent is told to stop. */
    public AgentLiveAck receive(Target target, AgentLiveSample sample) {
        Channel channel = channels.get(target);
        if (channel == null) {
            return new AgentLiveAck(false);
        }
        Instant now = clock.instant();
        synchronized (channel) {
            channel.agentAskedAt = now;
            boolean watched = channel.watched(now);
            if (!watched || channel.lastSampleAt != null && now.isBefore(channel.lastSampleAt.plus(MIN_GAP))) {
                return new AgentLiveAck(watched);
            }
            channel.add(sample, now);
            return new AgentLiveAck(true);
        }
    }

    /** Networks nobody watched for 5 minutes are forgotten (every minute). */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void forgetIdle() {
        forgetIdle(clock.instant());
    }

    void forgetIdle(Instant now) {
        channels.entrySet().removeIf(entry -> {
            Channel channel = entry.getValue();
            synchronized (channel) {
                return channel.watchUntil.plus(KEEP).isBefore(now);
            }
        });
    }

    int channelCount() {
        return channels.size();
    }

    private static final class Channel {
        private Instant watchingSince;
        private Instant watchUntil = Instant.EPOCH;
        private Instant agentAskedAt;
        private Instant lastSampleAt;
        private long seq;
        private final Deque<Point> points = new ArrayDeque<>();

        boolean watched(Instant now) {
            return watchUntil.isAfter(now);
        }

        void add(AgentLiveSample s, Instant now) {
            points.addLast(new Point(++seq, now, mbps(s.downloadBps()), mbps(s.uploadBps()), s.internetMs(),
                    s.internetMs() == null && Boolean.TRUE.equals(s.internetLost()), s.gatewayMs(),
                    s.gatewayMs() == null && Boolean.TRUE.equals(s.gatewayLost()), s.signalPercent(),
                    s.rxRateMbps(), s.txRateMbps()));
            lastSampleAt = now;
            while (points.size() > MAX_POINTS) {
                points.removeFirst();
            }
        }

        LiveView view(long after, Instant now) {
            while (!points.isEmpty() && points.peekFirst().at().isBefore(now.minus(KEEP))) {
                points.removeFirst();
            }
            List<Point> newer = points.stream().filter(p -> p.seq() > after).toList();
            List<Point> lastMinute = points.stream().filter(p -> !p.at().isBefore(now.minus(SUMMARY_WINDOW))).toList();
            return new LiveView(state(now), watchingSince, lastSampleAt, seq, newer, LiveSummary.of(lastMinute));
        }

        private String state(Instant now) {
            if (lastSampleAt != null && lastSampleAt.isAfter(now.minus(FRESH))) {
                return "LIVE";
            }
            // waiting since the page opened, or since the last sample if samples were arriving and stopped
            Instant since = lastSampleAt != null && lastSampleAt.isAfter(watchingSince) ? lastSampleAt : watchingSince;
            Duration waited = Duration.between(since, now);
            boolean askedSince = agentAskedAt != null && agentAskedAt.isAfter(since);
            if (waited.compareTo(AGENT_SILENT) >= 0 && !askedSince || waited.compareTo(NO_SAMPLES) >= 0) {
                return "NO_AGENT";
            }
            return "WAITING";
        }

        private static Double mbps(Long bps) {
            return bps == null ? null : Math.round(bps / 10_000.0) / 100.0;
        }
    }
}
