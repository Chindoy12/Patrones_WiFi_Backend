package com.wifisense.service;

import com.wifisense.repository.AgentDeviceRepository;
import com.wifisense.repository.AgentAlertRepository;
import com.wifisense.repository.AgentReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Deletes, every night, agent reports and devices older than the retention period. An agent sends close to 9 000
 * reports a month; keeping them forever would only grow the database and the data about the user's home.
 */
@Component
public class AgentRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AgentRetentionJob.class);

    private final AgentReportRepository reports;
    private final AgentDeviceRepository devices;
    private final AgentAlertRepository alerts;
    private final AgentProperties properties;
    private final Clock clock;

    public AgentRetentionJob(AgentReportRepository reports, AgentDeviceRepository devices, AgentAlertRepository alerts,
                             AgentProperties properties, Clock clock) {
        this.reports = reports;
        this.devices = devices;
        this.alerts = alerts;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${wifisense.agent.cleanup-cron:0 30 3 * * *}", zone = "UTC")
    @Transactional
    public void purge() {
        Instant before = Instant.now(clock).minus(properties.retention());
        int deletedReports = reports.deleteOlderThan(before);
        int deletedDevices = devices.deleteNotSeenSince(before);
        int deletedAlerts = alerts.deleteOlderThan(before);
        if (deletedReports + deletedDevices + deletedAlerts > 0) {
            log.info("Agent retention: deleted {} reports, {} devices and {} alerts older than {}", deletedReports,
                    deletedDevices, deletedAlerts, before);
        }
    }
}
