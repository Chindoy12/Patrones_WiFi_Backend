package com.wifisense.speedtest;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Per-user budget of speed-test transfers and saved tests in a sliding window. */
@Service
public class SpeedTestThrottle {

    private final Map<String, Deque<Instant>> transfers = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> tests = new ConcurrentHashMap<>();
    private final SpeedTestProperties properties;
    private final Clock clock;

    public SpeedTestThrottle(SpeedTestProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** One download or upload of the test; throws when the user is over budget. */
    public void acquireTransfer(String username) {
        acquire(transfers, username, properties.transfersPerWindow());
    }

    /** One saved test result; throws when the user is over budget. */
    public void acquireTest(String username) {
        acquire(tests, username, properties.testsPerWindow());
    }

    private void acquire(Map<String, Deque<Instant>> log, String username, int max) {
        Instant now = Instant.now(clock);
        Deque<Instant> recent = log.computeIfAbsent(username, key -> new ConcurrentLinkedDeque<>());
        synchronized (recent) {
            recent.removeIf(at -> at.isBefore(now.minus(properties.window())));
            if (recent.size() >= max) {
                throw new SpeedTestLimitException();
            }
            recent.add(now);
        }
    }
}
