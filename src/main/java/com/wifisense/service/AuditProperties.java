package com.wifisense.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Audit settings ({@code wifisense.audit.*}): {@code retention}, how long an audit row is kept. The table refuses to
 * delete anything younger than 30 days, so a shorter value is raised to 31 days instead of failing every night.
 */
@ConfigurationProperties("wifisense.audit")
public record AuditProperties(@DefaultValue("365d") Duration retention) {

    static final Duration TABLE_MIN = Duration.ofDays(31);

    public Duration effectiveRetention() {
        return retention.compareTo(TABLE_MIN) < 0 ? TABLE_MIN : retention;
    }
}
