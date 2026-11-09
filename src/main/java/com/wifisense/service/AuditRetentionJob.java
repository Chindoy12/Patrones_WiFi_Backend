package com.wifisense.service;

import com.wifisense.model.AuditAction;
import com.wifisense.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** Every night: deletes the audit rows older than the retention (365 days by default, never less than 31). */
@Component
public class AuditRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);

    private final AuditEventRepository events;
    private final AuditProperties properties;
    private final AuditService audit;
    private final Clock clock;

    public AuditRetentionJob(AuditEventRepository events, AuditProperties properties, AuditService audit, Clock clock) {
        this.events = events;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
    }

    @Scheduled(cron = "${wifisense.audit.purge-cron:0 40 3 * * *}", zone = "UTC")
    @Transactional
    public int purge() {
        Instant before = Instant.now(clock).minus(properties.effectiveRetention());
        int deleted = events.deleteOlderThan(before);
        if (deleted > 0) {
            log.info("Audit retention: {} rows older than {} deleted", deleted, before);
            audit.recordSystem(AuditAction.RETENTION_PURGED, deleted + " registros de auditoría de más de "
                    + properties.effectiveRetention().toDays() + " días");
        }
        return deleted;
    }
}
