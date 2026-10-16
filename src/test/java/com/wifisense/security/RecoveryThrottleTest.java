package com.wifisense.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecoveryThrottleTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-16T15:00:00Z"));
    private final RecoveryThrottle throttle = new RecoveryThrottle(new Clock() {
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now.get(); }
    });

    @Test
    void anAddressGetsFiveRequestsEveryFifteenMinutes() {
        for (int i = 0; i < RecoveryThrottle.MAX_PER_ADDRESS; i++) {
            throttle.acquireAddress("1.2.3.4");
        }
        assertThatThrownBy(() -> throttle.acquireAddress("1.2.3.4")).isInstanceOf(RecoveryBlockedException.class);

        now.set(now.get().plus(RecoveryThrottle.ADDRESS_WINDOW).plusSeconds(1));
        assertThatNoException().isThrownBy(() -> throttle.acquireAddress("1.2.3.4"));
    }

    @Test
    void anEmailGetsThreeLinksPerHour() {
        assertThat(throttle.acquireEmail("ana@gmail.com")).isTrue();
        assertThat(throttle.acquireEmail("ana@gmail.com")).isTrue();
        assertThat(throttle.acquireEmail("ana@gmail.com")).isTrue();
        assertThat(throttle.acquireEmail("ana@gmail.com")).isFalse();
        assertThat(throttle.acquireEmail("otra@gmail.com")).isTrue();
    }
}
