package com.wifisense.dto;

/**
 * What the agent prints after a report: enough to see that it arrived and what the AI concluded.
 * {@code nextReportSeconds}: when set, the agent sends its next report after that many seconds instead of its usual
 * five minutes (while the user identifies a device on the page).
 */
public record AgentReportAck(Long reportId, int deviceCount, int newDeviceCount, Integer securityScore,
                             String securityGrade, Boolean anomalyDetected, String summary, Integer nextReportSeconds) {

    public AgentReportAck(Long reportId, int deviceCount, int newDeviceCount, Integer securityScore,
                          String securityGrade, Boolean anomalyDetected, String summary) {
        this(reportId, deviceCount, newDeviceCount, securityScore, securityGrade, anomalyDetected, summary, null);
    }

    public AgentReportAck withNextReport(Integer seconds) {
        return new AgentReportAck(reportId, deviceCount, newDeviceCount, securityScore, securityGrade, anomalyDetected,
                summary, seconds);
    }
}
