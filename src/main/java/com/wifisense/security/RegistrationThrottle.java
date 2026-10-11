package com.wifisense.security;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Limits how many accounts one address can register per hour, so the public sign-up cannot be flooded. */
@Service
public class RegistrationThrottle {

    static final int MAX_PER_ADDRESS = 5;
    static final Duration WINDOW = Duration.ofHours(1);

    private final Map<String, Deque<Instant>> registrations = new ConcurrentHashMap<>();
    private final Clock clock;

    public RegistrationThrottle(Clock clock) {
        this.clock = clock;
    }

    public void check(String address) {
        Deque<Instant> recent = registrations.computeIfAbsent(address, key -> new ConcurrentLinkedDeque<>());
        Instant cutoff = Instant.now(clock).minus(WINDOW);
        recent.removeIf(at -> at.isBefore(cutoff));
        if (recent.size() >= MAX_PER_ADDRESS) {
            throw new RegistrationBlockedException();
        }
    }

    public void record(String address) {
        registrations.computeIfAbsent(address, key -> new ConcurrentLinkedDeque<>()).add(Instant.now(clock));
    }
}
