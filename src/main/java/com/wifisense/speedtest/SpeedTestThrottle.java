package com.wifisense.speedtest;

import com.wifisense.security.SlidingWindowLimiter;
import org.springframework.stereotype.Service;

import java.time.Clock;

/** Per-user budget of speed-test transfers and saved tests in a sliding window. */
@Service
public class SpeedTestThrottle {

    private final SlidingWindowLimiter transfers;
    private final SlidingWindowLimiter tests;

    public SpeedTestThrottle(SpeedTestProperties properties, Clock clock) {
        this.transfers = new SlidingWindowLimiter(properties.transfersPerWindow(), properties.window(), clock);
        this.tests = new SlidingWindowLimiter(properties.testsPerWindow(), properties.window(), clock);
    }

    /** One download or upload of the test; throws when the user is over budget. */
    public void acquireTransfer(String username) {
        if (!transfers.tryAcquire(username)) {
            throw new SpeedTestLimitException();
        }
    }

    /** One saved test result; throws when the user is over budget. */
    public void acquireTest(String username) {
        if (!tests.tryAcquire(username)) {
            throw new SpeedTestLimitException();
        }
    }
}
