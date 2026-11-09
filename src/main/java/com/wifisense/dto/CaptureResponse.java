package com.wifisense.dto;

import com.wifisense.model.CaptureStatus;
import com.wifisense.model.TrafficCapture;

import java.time.Instant;

/**
 * One capture as the list shows it. {@code failureMessage} and {@code stopMessage} are the sentences written by the
 * backend; {@code cancelRequested}: an administrator asked to stop it and the sensor has not delivered yet.
 */
public record CaptureResponse(Long id, CaptureStatus status, String purpose, Long requestedBy, Instant requestedAt,
                              Instant startedAt, Instant finishedAt, boolean cancelRequested, int maxSeconds,
                              int maxPackets, long maxBytes, String failureReason, String failureMessage,
                              String stopReason, String stopMessage, String captureTool, String captureToolLabel,
                              String interfaceName, Integer durationMs, Integer packetCount, Long byteCount,
                              String analyzedWith) {

    public static CaptureResponse from(TrafficCapture c) {
        return new CaptureResponse(c.getId(), c.getStatus(), c.getPurpose(), c.getRequestedBy(), c.getRequestedAt(),
                c.getStartedAt(), c.getFinishedAt(), c.cancelRequested(), c.getMaxSeconds(), c.getMaxPackets(),
                c.getMaxBytes(), c.getFailureReason() == null ? null : c.getFailureReason().name(),
                c.getFailureReason() == null ? null : c.getFailureReason().message(),
                c.getStopReason() == null ? null : c.getStopReason().name(),
                c.getStopReason() == null ? null : c.getStopReason().message(),
                c.getCaptureTool() == null ? null : c.getCaptureTool().name(),
                c.getCaptureTool() == null ? null : c.getCaptureTool().label(), c.getInterfaceName(),
                c.getDurationMs(), c.getPacketCount(), c.getByteCount(),
                c.getAnalyzedWith() == null ? null : c.getAnalyzedWith().name());
    }
}
