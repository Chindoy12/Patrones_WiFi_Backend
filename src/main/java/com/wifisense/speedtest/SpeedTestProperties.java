package com.wifisense.speedtest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

/**
 * Limits of the connection test. Every byte the test downloads leaves the server and is billed as egress, so sizes
 * stay small and each user has a budget per window.
 */
@ConfigurationProperties("wifisense.speedtest")
public record SpeedTestProperties(
        @DefaultValue("8MB") DataSize maxDownload,
        @DefaultValue("8MB") DataSize maxUpload,
        @DefaultValue("20") int transfersPerWindow,
        @DefaultValue("6") int testsPerWindow,
        @DefaultValue("10m") Duration window) {

    public long maxDownloadBytes() {
        return maxDownload.toBytes();
    }

    public long maxUploadBytes() {
        return maxUpload.toBytes();
    }
}
