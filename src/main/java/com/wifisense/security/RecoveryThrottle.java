package com.wifisense.security;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

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

    private final SlidingWindowLimiter byAddress;
    private final SlidingWindowLimiter byEmail;

    public RecoveryThrottle(Clock clock) {
        this.byAddress = new SlidingWindowLimiter(MAX_PER_ADDRESS, ADDRESS_WINDOW, clock);
        this.byEmail = new SlidingWindowLimiter(MAX_PER_EMAIL, EMAIL_WINDOW, clock);
    }

    /** Counts one request from this address; throws {@link RecoveryBlockedException} over the limit. */
    public void acquireAddress(String address) {
        if (!byAddress.tryAcquire(address)) {
            throw new RecoveryBlockedException();
        }
    }

    /** False when this email already received enough links lately. */
    public boolean acquireEmail(String email) {
        return byEmail.tryAcquire(email);
    }
}
