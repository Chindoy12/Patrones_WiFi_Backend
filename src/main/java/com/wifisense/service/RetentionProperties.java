package com.wifisense.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * How long the team's monitoring data is kept. {@code monitoring}: measurements, traffic, protocol statistics and
 * analyses older than this are deleted every night (reports cover at most 31 days, so 90 leaves room to compare
 * months). Resolved alerts are kept the same time after being resolved; open ones are never deleted.
 * {@code batchSize}: rows deleted per statement, so the cleanup never locks a table for long.
 */
@ConfigurationProperties("wifisense.retention")
public record RetentionProperties(@DefaultValue("90d") Duration monitoring,
                                  @DefaultValue("5000") int batchSize) {

    public RetentionProperties {
        if (monitoring.compareTo(Duration.ofDays(31)) < 0) {
            throw new IllegalStateException(
                    "wifisense.retention.monitoring must be at least 31d: reports cover up to 31 days");
        }
        if (batchSize < 100) {
            throw new IllegalStateException("wifisense.retention.batch-size must be at least 100");
        }
    }
}
