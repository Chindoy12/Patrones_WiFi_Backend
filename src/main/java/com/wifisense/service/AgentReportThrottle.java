package com.wifisense.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** At most one accepted report per agent key every {@code minInterval}. */
@Component
public class AgentReportThrottle {

    private final Map<Long, Instant> lastAccepted = new ConcurrentHashMap<>();
    private final AgentProperties properties;
    private final Clock clock;

    public AgentReportThrottle(AgentProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void acquire(Long keyId) {
        Instant now = Instant.now(clock);
        lastAccepted.compute(keyId, (id, previous) -> {
            if (previous != null && previous.plus(properties.minInterval()).isAfter(now)) {
                long wait = Duration.between(now, previous.plus(properties.minInterval())).toSeconds() + 1;
                throw new AgentReportLimitException(wait);
            }
            return now;
        });
    }

    /** A revoked key will never report again; its entry is not needed. */
    public void forget(Long keyId) {
        lastAccepted.remove(keyId);
    }
}
