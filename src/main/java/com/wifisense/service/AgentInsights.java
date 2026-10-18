package com.wifisense.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wifisense.analysis.*;
import com.wifisense.dto.AgentReportRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;

/**
 * What the AI says about one report of the agent: the security audit of the Wi-Fi and the router, the quality of the
 * connection and the best Wi-Fi channel, plus the Wi-Fi details the page shows. The agent of Mi Wi-Fi and the sensor
 * of a team network send the same report, so both use this. Every verdict is optional: when the AI is down, or answers
 * something unexpected, it is null and the report is saved without it. Never called inside a database transaction.
 */
class AgentInsights {

    static final Set<String> SECURITY_GRADES = Set.of("SECURE", "ACCEPTABLE", "AT_RISK", "CRITICAL");
    private static final Logger log = LoggerFactory.getLogger(AgentInsights.class);

    private final AiAnalysisClient ai;
    private final ObjectMapper json;

    AgentInsights(AiAnalysisClient ai, ObjectMapper json) {
        this.ai = ai;
        this.json = json;
    }

    ObjectNode wifiDetails(AgentReportRequest request) {
        ObjectNode wifi = json.createObjectNode();
        wifi.put("cipher", request.cipherForAi());
        wifi.put("signalPercent", request.signalPercent());
        wifi.put("rxRateMbps", request.rxRateMbps());
        wifi.put("txRateMbps", request.txRateMbps());
        wifi.put("neighborsSameChannel", request.neighborsSameChannel());
        wifi.put("neighborsTotal", request.neighborsTotal());
        wifi.put("note", request.wifiNote());
        wifi.put("platform", request.platform());
        return wifi;
    }

    JsonNode assessSecurity(AgentReportRequest request, int deviceCount, int newDevices) {
        try {
            JsonNode answer = ai.securityAssess(new AiSecurityRequest(
                    Optional.ofNullable(request.authentication()).orElse("UNKNOWN"), request.cipherForAi(),
                    request.openPortsOrEmpty().stream().distinct().sorted().toList(), deviceCount, newDevices,
                    request.band(), request.channel(), request.neighborsSameChannel()));
            int score = answer.path("score").asInt(-1);
            if (score < 0 || score > 100 || !SECURITY_GRADES.contains(answer.path("grade").asText())) {
                log.warn("Ignoring unexpected security answer from the AI service");
                return null;
            }
            return answer;
        } catch (AiServiceUnavailableException e) {
            log.warn("Agent report saved without security audit: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Best channel from the neighbours the agent saw. Old agents do not send them, and without them there is nothing
     * to compare, so then nothing is asked to the AI and Mi Wi-Fi simply shows no recommendation.
     */
    JsonNode recommendChannel(AgentReportRequest request) {
        if (request.channelCountsOrEmpty().isEmpty()) {
            return null;
        }
        try {
            JsonNode answer = ai.channelRecommend(new AiChannelRequest(request.band(), request.channel(),
                    request.channelCountsOrEmpty().stream()
                            .map(c -> new AiChannelRequest.ChannelCount(c.channel(), c.networks()))
                            .toList()));
            if (answer == null || !answer.path("shouldChange").isBoolean() || !answer.hasNonNull("summary")) {
                log.warn("Ignoring unexpected channel answer from the AI service");
                return null;
            }
            return answer;
        } catch (AiServiceUnavailableException e) {
            log.warn("Agent report saved without channel recommendation: {}", e.getMessage());
            return null;
        }
    }

    JsonNode scoreQuality(AgentReportRequest request) {
        if (request.latencyMs() == null || request.jitterMs() == null || request.latencyMs() <= 0) {
            return null;
        }
        try {
            JsonNode answer = ai.qualityScore(new AiQualityRequest(request.latencyMs(), request.jitterMs(),
                    request.packetLossPct(), null, null, null,
                    request.signalDbm() == null ? null : request.signalDbm().doubleValue()));
            int score = answer.path("score").asInt(-1);
            return score >= 0 && score <= 100 ? answer : null;
        } catch (AiServiceUnavailableException e) {
            log.warn("Agent report saved without quality score: {}", e.getMessage());
            return null;
        }
    }
}
