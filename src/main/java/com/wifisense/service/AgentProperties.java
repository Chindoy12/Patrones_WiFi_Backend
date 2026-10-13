package com.wifisense.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Limits of the WiFiSense agent. {@code minInterval}: an agent cannot report more often than this (it reports every
 * 5 minutes; this stops a leaked key from flooding the database). {@code retention}: reports and devices not seen for
 * longer are deleted every night. {@code onlineWindow}: an agent that reported within this time counts as connected.
 */
@ConfigurationProperties("wifisense.agent")
public record AgentProperties(
        @DefaultValue("50s") Duration minInterval,
        @DefaultValue("30d") Duration retention,
        @DefaultValue("15m") Duration onlineWindow,
        @DefaultValue("20") int analysisWindow,
        @DefaultValue("48") int historySize) {
}
