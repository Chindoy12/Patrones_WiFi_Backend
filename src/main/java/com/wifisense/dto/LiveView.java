package com.wifisense.dto;

import java.time.Instant;
import java.util.List;

/**
 * What «En vivo» shows: the state of the live measurement, the samples after the one the page already has, and a
 * summary of the last minute with what it means.
 *
 * @param state      {@code LIVE} (a sample arrived in the last seconds), {@code WAITING} (the agent was told and
 *                   should start within 30 seconds) or {@code NO_AGENT} (nobody answered: the computer is off, has no
 *                   internet or its agent is older than 2.1.0)
 * @param lastSeq    the number of the newest sample; the page sends it back as {@code after} to get only new ones
 * @param points     the samples after {@code after}, oldest first (at most the last 5 minutes)
 * @param summary    the last minute
 */
public record LiveView(String state, Instant watchingSince, Instant lastSampleAt, long lastSeq, List<Point> points,
                       Summary summary) {

    /** One sample: Mbps instead of bits per second, and when the server received it. */
    public record Point(long seq, Instant at, Double downloadMbps, Double uploadMbps, Double internetMs,
                        boolean internetLost, Double gatewayMs, boolean gatewayLost, Integer signalPercent,
                        Double rxRateMbps, Double txRateMbps) {
    }

    /**
     * The last minute. Latencies are medians, {@code jitterMs} the mean change between consecutive answers and
     * {@code lossPct} the share of samples that internet did not answer. Null when there is no data for it.
     */
    public record Summary(int samples, Double downloadMbps, Double uploadMbps, Double peakDownloadMbps,
                          Double peakUploadMbps, Double internetMs, Double jitterMs, Double lossPct, Double gatewayMs,
                          Integer signalPercent, List<Note> notes) {
    }

    /** What the numbers mean, from fixed rules: {@code OK}, {@code WARN} or {@code BAD}. */
    public record Note(String level, String text) {
    }
}
