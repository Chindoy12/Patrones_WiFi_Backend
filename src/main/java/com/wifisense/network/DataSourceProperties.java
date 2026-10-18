package com.wifisense.network;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * {@code demoEnabled}: whether networks can use the demo sources (simulation, or the backend's own connectivity) and
 * the monitoring job collects them. Off by default, so production only shows what the sensors measure; local demos
 * turn it on with DEMO_DATA_SOURCES=true.
 */
@ConfigurationProperties("wifisense.data-sources")
public record DataSourceProperties(@DefaultValue Simulation simulation, @DefaultValue SystemProbe systemProbe,
                                   @DefaultValue("false") boolean demoEnabled) {

    public record Simulation(@DefaultValue("0.1") double anomalyProbability) {
    }

    public record SystemProbe(@DefaultValue("1.1.1.1") String host,
                              @DefaultValue("443") int port,
                              @DefaultValue("5") int attempts,
                              @DefaultValue("1s") Duration timeout) {
    }
}
