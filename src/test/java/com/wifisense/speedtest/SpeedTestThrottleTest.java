package com.wifisense.speedtest;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpeedTestThrottleTest {

    private Instant now = Instant.parse("2026-10-12T12:00:00Z");
    private final Clock clock = new Clock() {
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    };
    private final SpeedTestProperties properties = new SpeedTestProperties(DataSize.ofMegabytes(8),
            DataSize.ofMegabytes(8), 3, 2, Duration.ofMinutes(10));
    private final SpeedTestThrottle throttle = new SpeedTestThrottle(properties, clock);

    @Test
    void transfersAreLimitedPerUserAndWindow() {
        for (int i = 0; i < 3; i++) {
            throttle.acquireTransfer("ana");
        }

        assertThatThrownBy(() -> throttle.acquireTransfer("ana"))
                .isInstanceOf(SpeedTestLimitException.class)
                .hasMessage("Hiciste muchas pruebas seguidas. Espera unos minutos e inténtalo de nuevo.");
        assertThatCode(() -> throttle.acquireTransfer("beto")).doesNotThrowAnyException();
    }

    @Test
    void budgetComesBackWhenTheWindowPasses() {
        throttle.acquireTest("ana");
        throttle.acquireTest("ana");
        assertThatThrownBy(() -> throttle.acquireTest("ana")).isInstanceOf(SpeedTestLimitException.class);

        now = now.plus(Duration.ofMinutes(11));

        assertThatCode(() -> throttle.acquireTest("ana")).doesNotThrowAnyException();
    }

    @Test
    void savedTestsAndTransfersHaveSeparateBudgets() {
        throttle.acquireTest("ana");
        throttle.acquireTest("ana");

        assertThatCode(() -> throttle.acquireTransfer("ana")).doesNotThrowAnyException();
    }
}
