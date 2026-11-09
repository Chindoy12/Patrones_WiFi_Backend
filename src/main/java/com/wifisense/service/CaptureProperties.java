package com.wifisense.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

/**
 * Limits of the technical captures, all configurable ({@code wifisense.capture.*}).
 * <ul>
 *   <li>{@code defaultSeconds}, {@code defaultPackets}: what the form proposes.</li>
 *   <li>{@code maxSeconds}, {@code maxPackets}, {@code maxBytes}: the most an administrator may ask for (the table
 *       accepts at most 300 s, 100 000 packets and 100 MB; these defaults stay well below).</li>
 *   <li>{@code startTimeout}: a capture the sensor did not pick up in this time fails as AGENT_NO_RESPONSE.</li>
 *   <li>{@code resultGrace}: once started, the sensor has the capture's duration plus this to deliver it.</li>
 *   <li>{@code pollMinInterval}: a sensor cannot ask for pending captures more often than this.</li>
 *   <li>{@code minAgentVersion}: the first agent that knows how to capture.</li>
 *   <li>{@code retention}: captures (with their statistics and findings) are deleted every night after this. The raw
 *       capture file never leaves the sensor's computer: the agent deletes it right after the analysis.</li>
 * </ul>
 */
@ConfigurationProperties("wifisense.capture")
public record CaptureProperties(
        @DefaultValue("30") int defaultSeconds,
        @DefaultValue("5000") int defaultPackets,
        @DefaultValue("120") int maxSeconds,
        @DefaultValue("20000") int maxPackets,
        @DefaultValue("20MB") DataSize maxBytes,
        @DefaultValue("3m") Duration startTimeout,
        @DefaultValue("90s") Duration resultGrace,
        @DefaultValue("10s") Duration pollMinInterval,
        @DefaultValue("2.0.0") String minAgentVersion,
        @DefaultValue("90d") Duration retention) {

    /** The table accepts at most these; a configuration above them is lowered, never trusted. */
    static final int TABLE_MAX_SECONDS = 300;
    static final int TABLE_MAX_PACKETS = 100_000;
    static final long TABLE_MAX_BYTES = 100L * 1024 * 1024;
    static final long TABLE_MIN_BYTES = 1024L * 1024;

    public int secondsCap() {
        return Math.max(5, Math.min(maxSeconds, TABLE_MAX_SECONDS));
    }

    public int packetsCap() {
        return Math.max(100, Math.min(maxPackets, TABLE_MAX_PACKETS));
    }

    public long bytesCap() {
        return Math.max(TABLE_MIN_BYTES, Math.min(maxBytes.toBytes(), TABLE_MAX_BYTES));
    }
}
