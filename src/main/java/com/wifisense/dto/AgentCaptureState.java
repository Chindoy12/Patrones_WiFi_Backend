package com.wifisense.dto;

/** Asked by the sensor while it captures: {@code stop} is true when an administrator asked to stop the capture. */
public record AgentCaptureState(Long captureId, boolean stop) {
}
