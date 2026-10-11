package com.wifisense.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrationThrottleTest {

    /** A clock the test can move forward. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-10T12:00:00Z");

        void advance(java.time.Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MovableClock clock = new MovableClock();
    private final RegistrationThrottle throttle = new RegistrationThrottle(clock);

    private void fill(String address) {
        for (int i = 0; i < RegistrationThrottle.MAX_PER_ADDRESS; i++) {
            throttle.check(address);
            throttle.record(address);
        }
    }

    @Test
    void blocksAnAddressAfterTheLimitAndOnlyThatAddress() {
        fill("1.1.1.1");

        assertThatThrownBy(() -> throttle.check("1.1.1.1")).isInstanceOf(RegistrationBlockedException.class);
        assertThatCode(() -> throttle.check("2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    void theLimitResetsAfterTheWindow() {
        fill("1.1.1.1");
        clock.advance(RegistrationThrottle.WINDOW.plusSeconds(1));

        assertThatCode(() -> throttle.check("1.1.1.1")).doesNotThrowAnyException();
    }
}
