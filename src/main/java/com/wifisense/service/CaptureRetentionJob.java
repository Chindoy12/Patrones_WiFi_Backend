package com.wifisense.service;

import com.wifisense.model.AuditAction;
import com.wifisense.repository.TrafficCaptureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Every night deletes the finished captures older than {@code wifisense.capture.retention} (90 days), with their
 * statistics and findings. The raw capture files never reach the server: the agent deletes them on the sensor's
 * computer right after the analysis.
 */
@Component
public class CaptureRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(CaptureRetentionJob.class);

    private final TrafficCaptureRepository captures;
    private final CaptureProperties properties;
    private final AuditService audit;
    private final Clock clock;

    public CaptureRetentionJob(TrafficCaptureRepository captures, CaptureProperties properties, AuditService audit,
                               Clock clock) {
        this.captures = captures;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
    }

    @Scheduled(cron = "${wifisense.capture.cleanup-cron:0 50 3 * * *}", zone = "UTC")
    @Transactional
    public int purge() {
        int deleted = captures.deleteFinishedBefore(Instant.now(clock).minus(properties.retention()), CaptureQueue.ACTIVE);
        if (deleted > 0) {
            log.info("Capture retention: {} captures deleted", deleted);
            audit.recordSystem(AuditAction.RETENTION_PURGED, deleted + " capturas de más de "
                    + properties.retention().toDays() + " días");
        }
        return deleted;
    }
}
