package com.wifisense.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowLimiterTest {

    /** A clock the test moves by hand. */
    private static final class ManualClock extends Clock {
        private Instant now = Instant.parse("2026-11-20T15:00:00Z");

        void advance(Duration by) { now = now.plus(by); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final ManualClock clock = new ManualClock();
    private final SlidingWindowLimiter limiter = new SlidingWindowLimiter(3, Duration.ofMinutes(10), clock);

    @Test
    void allowsUpToTheLimitPerKeyAndEachKeyCountsApart() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        }
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
        assertThat(limiter.tryAcquire("2.2.2.2")).isTrue();
    }

    @Test
    void anEventLeavesTheCountWhenItIsOlderThanTheWindow() {
        limiter.tryAcquire("a");
        clock.advance(Duration.ofMinutes(6));
        limiter.tryAcquire("a");
        limiter.tryAcquire("a");
        assertThat(limiter.tryAcquire("a")).isFalse();

        clock.advance(Duration.ofMinutes(5));  // the first one is now 11 minutes old
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
    }

    @Test
    void hasRoomOnlyLooksAndRecordCountsEvenOverTheLimit() {
        assertThat(limiter.hasRoom("b")).isTrue();
        assertThat(limiter.hasRoom("b")).isTrue();  // looking does not count
        limiter.record("b");
        limiter.record("b");
        limiter.record("b");
        assertThat(limiter.hasRoom("b")).isFalse();
        limiter.record("b");
        clock.advance(Duration.ofMinutes(11));
        assertThat(limiter.hasRoom("b")).isTrue();
    }
}
