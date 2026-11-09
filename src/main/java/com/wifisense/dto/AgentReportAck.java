package com.wifisense.dto;

/**
 * What the agent prints after a report: enough to see that it arrived and what the AI concluded.
 * {@code nextReportSeconds}: when set, the agent sends its next report after that many seconds instead of its usual
 * five minutes (while the user identifies a device on the page).
 * {@code scanId}: when set, the agent scans the nearby Wi-Fi networks now and delivers the result to that scan
 * (asked from the page, or due by the schedule). Agents older than 1.9.0 ignore it.
 * {@code captureId}: only for the sensor of a team network; an administrator asked for a technical capture. The agent
 * takes it at once with GET /api/agent/captures/next (which gives its limits) and runs it if its computer allows it
 * (agents older than 2.0.0 ignore it).
 */
public record AgentReportAck(Long reportId, int deviceCount, int newDeviceCount, Integer securityScore,
                             String securityGrade, Boolean anomalyDetected, String summary, Integer nextReportSeconds,
                             Long scanId, Long captureId) {

    public AgentReportAck(Long reportId, int deviceCount, int newDeviceCount, Integer securityScore,
                          String securityGrade, Boolean anomalyDetected, String summary) {
        this(reportId, deviceCount, newDeviceCount, securityScore, securityGrade, anomalyDetected, summary, null, null,
                null);
    }

    public AgentReportAck(Long reportId, int deviceCount, int newDeviceCount, Integer securityScore,
                          String securityGrade, Boolean anomalyDetected, String summary, Integer nextReportSeconds) {
        this(reportId, deviceCount, newDeviceCount, securityScore, securityGrade, anomalyDetected, summary,
                nextReportSeconds, null, null);
    }

    public AgentReportAck withNextReport(Integer seconds) {
        return new AgentReportAck(reportId, deviceCount, newDeviceCount, securityScore, securityGrade, anomalyDetected,
                summary, seconds, scanId, captureId);
    }

    public AgentReportAck withScan(Long scan) {
        return new AgentReportAck(reportId, deviceCount, newDeviceCount, securityScore, securityGrade, anomalyDetected,
                summary, nextReportSeconds, scan, captureId);
    }

    public AgentReportAck withCapture(Long capture) {
        return new AgentReportAck(reportId, deviceCount, newDeviceCount, securityScore, securityGrade, anomalyDetected,
                summary, nextReportSeconds, scanId, capture);
    }
}
