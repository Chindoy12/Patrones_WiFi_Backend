package com.wifisense.security;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Limits recovery requests: per address (so nobody can flood mailboxes or probe many emails) and per email (at most
 * a few links per hour; extra requests are answered the same way but send nothing).
 */
@Service
public class RecoveryThrottle {

    static final int MAX_PER_ADDRESS = 5;
    static final Duration ADDRESS_WINDOW = Duration.ofMinutes(15);
    static final int MAX_PER_EMAIL = 3;
    static final Duration EMAIL_WINDOW = Duration.ofHours(1);

    private final Map<String, Deque<Instant>> byAddress = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> byEmail = new ConcurrentHashMap<>();
    private final Clock clock;

    public RecoveryThrottle(Clock clock) {
        this.clock = clock;
    }

    /** Counts one request from this address; throws {@link RecoveryBlockedException} over the limit. */
    public void acquireAddress(String address) {
        if (!acquire(byAddress, address, MAX_PER_ADDRESS, ADDRESS_WINDOW)) {
            throw new RecoveryBlockedException();
        }
    }

    /** False when this email already received enough links lately. */
    public boolean acquireEmail(String email) {
        return acquire(byEmail, email, MAX_PER_EMAIL, EMAIL_WINDOW);
    }

    private boolean acquire(Map<String, Deque<Instant>> log, String key, int max, Duration window) {
        Instant now = Instant.now(clock);
        Deque<Instant> recent = log.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (recent) {
            recent.removeIf(at -> at.isBefore(now.minus(window)));
            if (recent.size() >= max) {
                return false;
            }
            recent.add(now);
            return true;
        }
    }
}
