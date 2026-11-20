package com.wifisense.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifisense.dto.AgentOverview;
import com.wifisense.model.ObservedDevice;
import com.wifisense.model.WifiReport;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the parts of an {@link AgentOverview} that are the same for the agent of a home (Mi Wi-Fi) and for the
 * sensor of a team network: the latest report, the history and the list of devices. Each service only says where its
 * reports and devices come from.
 */
class AgentOverviewAssembler {

    /** A device that joined in this window is shown as "recent". */
    static final Duration RECENT_DEVICE = Duration.ofHours(24);

    /** Not authorized first, then the most recently seen, then the oldest known. */
    private static final Comparator<ObservedDevice> LISTING_ORDER = Comparator.comparing(ObservedDevice::isAuthorized)
            .thenComparing(Comparator.comparing(ObservedDevice::getLastSeenAt).reversed())
            .thenComparing(ObservedDevice::getFirstSeenAt);

    private final ObjectMapper json;

    AgentOverviewAssembler(ObjectMapper json) {
        this.json = json;
    }

    AgentOverview.Report report(WifiReport report) {
        return report == null ? null : AgentOverview.Report.of(report, details(report.getDetails()));
    }

    /** The reports from the oldest to the newest, as the chart draws them. */
    List<AgentOverview.HistoryPoint> history(List<? extends WifiReport> recent) {
        return recent.stream()
                .sorted(Comparator.comparing(WifiReport::getReceivedAt))
                .map(AgentOverview.HistoryPoint::of)
                .toList();
    }

    /**
     * {@code all}: the devices of the network from the first one seen. "Online" are those in the latest report;
     * "recent" those that joined in the last 24 hours, except the ones of the first report, which were only learned.
     */
    List<AgentOverview.Device> devices(List<? extends ObservedDevice> all, WifiReport latest, Instant now) {
        if (all.isEmpty()) {
            return List.of();
        }
        Instant learnedAt = all.get(0).getFirstSeenAt();
        Instant latestAt = latest == null ? null : latest.getReceivedAt();
        return all.stream()
                .sorted(LISTING_ORDER)
                .map(d -> AgentOverview.Device.of(d, latestAt != null && !d.getLastSeenAt().isBefore(latestAt),
                        d.getFirstSeenAt().isAfter(learnedAt) && d.getFirstSeenAt().isAfter(now.minus(RECENT_DEVICE))))
                .toList();
    }

    private JsonNode details(String text) {
        try {
            return json.readTree(text == null ? "{}" : text);
        } catch (JsonProcessingException e) {
            return json.createObjectNode();
        }
    }
}
