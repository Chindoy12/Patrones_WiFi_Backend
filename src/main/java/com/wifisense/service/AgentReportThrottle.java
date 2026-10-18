package com.wifisense.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** At most one accepted report per agent key (or sensor key of a team network) every {@code minInterval}. */
@Component
public class AgentReportThrottle {

    private final Map<String, Instant> lastAccepted = new ConcurrentHashMap<>();
    private final AgentProperties properties;
    private final Clock clock;

    public AgentReportThrottle(AgentProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void acquire(Long keyId) {
        acquire("agent:" + keyId);
    }

    /** Sensors have their own ids, so they are counted apart from the agents of Mi Wi-Fi. */
    public void acquireSensor(Long sensorId) {
        acquire("sensor:" + sensorId);
    }

    private void acquire(String key) {
        Instant now = Instant.now(clock);
        lastAccepted.compute(key, (id, previous) -> {
            if (previous != null && previous.plus(properties.minInterval()).isAfter(now)) {
                long wait = Duration.between(now, previous.plus(properties.minInterval())).toSeconds() + 1;
                throw new AgentReportLimitException(wait);
            }
            return now;
        });
    }

    /** A revoked key will never report again; its entry is not needed. */
    public void forget(Long keyId) {
        lastAccepted.remove("agent:" + keyId);
    }

    public void forgetSensor(Long sensorId) {
        lastAccepted.remove("sensor:" + sensorId);
    }
}
