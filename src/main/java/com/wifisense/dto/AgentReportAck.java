package com.wifisense.dto;

/** What the agent prints after a report: enough to see that it arrived and what the AI concluded. */
public record AgentReportAck(Long reportId, int deviceCount, int newDeviceCount, Integer securityScore,
                             String securityGrade, Boolean anomalyDetected, String summary) {
}
