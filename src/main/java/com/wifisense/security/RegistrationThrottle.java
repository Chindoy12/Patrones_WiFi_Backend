package com.wifisense.security;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

/** Limits how many accounts one address can register per hour, so the public sign-up cannot be flooded. */
@Service
public class RegistrationThrottle {

    static final int MAX_PER_ADDRESS = 5;
    static final Duration WINDOW = Duration.ofHours(1);

    private final SlidingWindowLimiter registrations;

    public RegistrationThrottle(Clock clock) {
        this.registrations = new SlidingWindowLimiter(MAX_PER_ADDRESS, WINDOW, clock);
    }

    /** Before creating the account: throws {@link RegistrationBlockedException} when this address is at its limit. */
    public void check(String address) {
        if (!registrations.hasRoom(address)) {
            throw new RegistrationBlockedException();
        }
    }

    /** After the account was created (a sign-up that failed does not count). */
    public void record(String address) {
        registrations.record(address);
    }
}
