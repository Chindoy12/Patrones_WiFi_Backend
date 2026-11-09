package com.wifisense.dto;

/**
 * What the sensor receives: the number of the capture to run and its limits. Never a command: the agent decides
 * how to capture, checks its own consent and tools, and refuses whatever it is not allowed to do.
 */
public record AgentCaptureTask(Long captureId, int maxSeconds, int maxPackets, long maxBytes) {
}
