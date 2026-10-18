package com.wifisense.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class AgentReportThrottleTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-12T12:00:00Z"));
    private final Clock clock = new Clock() {
        public ZoneOffset getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId zone) { return this; }
        public Instant instant() { return now.get(); }
    };
    private final AgentReportThrottle throttle = new AgentReportThrottle(
            new AgentProperties(Duration.ofSeconds(50), Duration.ofDays(30), Duration.ofMinutes(15), 20, 48), clock);

    @Test
    void acceptsOneReportPerIntervalAndSaysWhenToRetry() {
        throttle.acquire(1L);

        now.set(now.get().plusSeconds(20));
        assertThatThrownBy(() -> throttle.acquire(1L)).isInstanceOfSatisfying(AgentReportLimitException.class,
                e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(31));
        assertThatNoException().isThrownBy(() -> throttle.acquire(2L));

        now.set(now.get().plusSeconds(30));
        assertThatNoException().isThrownBy(() -> throttle.acquire(1L));
    }

    @Test
    void aRejectedReportDoesNotPushTheWindow() {
        throttle.acquire(1L);
        now.set(now.get().plusSeconds(49));
        assertThatThrownBy(() -> throttle.acquire(1L)).isInstanceOf(AgentReportLimitException.class);

        now.set(now.get().plusSeconds(1));
        assertThatNoException().isThrownBy(() -> throttle.acquire(1L));
    }

    @Test
    void forgettingAKeyFreesIt() {
        throttle.acquire(1L);
        throttle.forget(1L);

        assertThatNoException().isThrownBy(() -> throttle.acquire(1L));
    }

    @Test
    void aSensorAndAnAgentWithTheSameIdAreCountedApart() {
        throttle.acquire(1L);

        assertThatNoException().isThrownBy(() -> throttle.acquireSensor(1L));
        assertThatThrownBy(() -> throttle.acquireSensor(1L)).isInstanceOf(AgentReportLimitException.class);
        throttle.forgetSensor(1L);
        assertThatNoException().isThrownBy(() -> throttle.acquireSensor(1L));
        assertThatThrownBy(() -> throttle.acquire(1L)).isInstanceOf(AgentReportLimitException.class);
    }
}
