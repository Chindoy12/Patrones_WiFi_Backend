package com.wifisense.service;

import com.wifisense.repository.AgentDeviceRepository;
import com.wifisense.repository.AgentAlertRepository;
import com.wifisense.repository.AgentReportRepository;
import com.wifisense.repository.SensorDeviceRepository;
import com.wifisense.repository.SensorReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Deletes, every night, agent reports and devices older than the retention period, of Mi Wi-Fi and of the sensors of
 * the team networks. An agent sends close to 9 000 reports a month; keeping them forever would only grow the database
 * and the data about the user's home. The measurements of the team networks follow their own retention (90 days).
 */
@Component
public class AgentRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AgentRetentionJob.class);

    private final AgentReportRepository reports;
    private final AgentDeviceRepository devices;
    private final AgentAlertRepository alerts;
    private final SensorReportRepository sensorReports;
    private final SensorDeviceRepository sensorDevices;
    private final AgentProperties properties;
    private final DeviceSessionTracker sessions;
    private final Clock clock;

    public AgentRetentionJob(AgentReportRepository reports, AgentDeviceRepository devices, AgentAlertRepository alerts,
                             SensorReportRepository sensorReports, SensorDeviceRepository sensorDevices,
                             AgentProperties properties, Clock clock, DeviceSessionTracker sessions) {
        this.reports = reports;
        this.devices = devices;
        this.alerts = alerts;
        this.sensorReports = sensorReports;
        this.sensorDevices = sensorDevices;
        this.properties = properties;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Scheduled(cron = "${wifisense.agent.cleanup-cron:0 30 3 * * *}", zone = "UTC")
    @Transactional
    public void purge() {
        Instant before = Instant.now(clock).minus(properties.retention());
        int deletedReports = reports.deleteOlderThan(before) + sensorReports.deleteOlderThan(before);
        int deletedDevices = devices.deleteNotSeenSince(before) + sensorDevices.deleteNotSeenSince(before);
        int deletedAlerts = alerts.deleteOlderThan(before);
        int deletedPeriods = sessions.deleteOlderThan(before);
        if (deletedReports + deletedDevices + deletedAlerts + deletedPeriods > 0) {
            log.info("Agent retention: deleted {} reports, {} devices, {} alerts and {} connection periods older than {}",
                    deletedReports, deletedDevices, deletedAlerts, deletedPeriods, before);
        }
    }
}
