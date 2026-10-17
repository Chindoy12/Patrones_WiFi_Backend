package com.wifisense.dto;

import java.time.Instant;
import java.util.List;

/**
 * The real home networks of the end users, for the team panel (ADMIN and ANALYST). It carries what the team needs to
 * support them (agent connected, security, quality, anomalies, channel) and nothing about their devices: no names,
 * makers or addresses ever reach the team.
 */
public record TeamNetworksOverview(Totals totals, List<NetworkRow> networks, Instant generatedAt) {

    /**
     * {@code atRisk}: networks whose latest security grade is AT_RISK or CRITICAL. {@code anomaliesLast24h}: networks
     * with at least one anomaly in the last 24 hours. {@code devices}: devices in the latest report of each network.
     */
    public record Totals(long endUsers, int networks, int withAgent, int agentsOnline, int atRisk,
                         long anomaliesLast24h, int devices) {
    }

    /**
     * One home network and its latest report. {@code owner} is only complete for an administrator (an analyst sees
     * it masked, like "an•••@gmail.com"); {@code needsAttention} puts networks with a problem first.
     */
    public record NetworkRow(Long id, String name, String owner, String provider, Integer contractedMbps,
                             Instant createdAt, boolean hasAgent, boolean agentOnline, Instant lastReportAt,
                             String agentVersion, Integer deviceCount, Integer securityScore, String securityGrade,
                             Integer qualityScore, Boolean anomalyDetected, String anomalyType, String band,
                             Integer channel, Integer recommendedChannel, boolean channelChangeSuggested,
                             boolean needsAttention) {
    }
}
