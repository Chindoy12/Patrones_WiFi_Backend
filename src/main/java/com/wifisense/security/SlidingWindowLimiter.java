package com.wifisense.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * At most {@code max} events per key (an address, an email, a user) in any window of the given length. Every limit of
 * the backend that counts attempts in memory (registrations, recovery requests, installation codes, speed tests) is
 * one of these, so the counting is written and tested once. In memory: a restart starts the count again.
 */
public final class SlidingWindowLimiter {

    private final Map<String, Deque<Instant>> events = new ConcurrentHashMap<>();
    private final int max;
    private final Duration window;
    private final Clock clock;

    public SlidingWindowLimiter(int max, Duration window, Clock clock) {
        this.max = max;
        this.window = window;
        this.clock = clock;
    }

    /** Counts one event for this key and returns true; false, without counting it, when the key is at its limit. */
    public boolean tryAcquire(String key) {
        Instant now = Instant.now(clock);
        Deque<Instant> recent = recentOf(key, now);
        synchronized (recent) {
            if (recent.size() >= max) {
                return false;
            }
            recent.add(now);
            return true;
        }
    }

    /** Whether one more event of this key would still be within the limit (nothing is counted). */
    public boolean hasRoom(String key) {
        Deque<Instant> recent = recentOf(key, Instant.now(clock));
        synchronized (recent) {
            return recent.size() < max;
        }
    }

    /** Counts one event of this key, at its limit or not (for events that are checked before they happen). */
    public void record(String key) {
        Instant now = Instant.now(clock);
        Deque<Instant> recent = recentOf(key, now);
        synchronized (recent) {
            recent.add(now);
        }
    }

    private Deque<Instant> recentOf(String key, Instant now) {
        Deque<Instant> recent = events.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (recent) {
            recent.removeIf(at -> at.isBefore(now.minus(window)));
        }
        return recent;
    }
}
