package com.wifisense.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.wifisense.analysis.AiAnalysisClient;
import com.wifisense.analysis.AiTrafficRequest;
import com.wifisense.model.CaptureFinding;
import com.wifisense.model.CaptureStatus;
import com.wifisense.model.FindingSeverity;
import com.wifisense.model.FindingSource;
import com.wifisense.model.Measurement;
import com.wifisense.model.TrafficCapture;
import com.wifisense.repository.MeasurementRepository;
import com.wifisense.repository.TrafficCaptureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Analyses a completed capture: the backend's facts always, plus the AI's inferences; when the AI is not available,
 * the backend's own fallback inference instead, and the capture says so ({@code analyzedWith} RULES). The AI is
 * called outside any transaction (it can take seconds) and only receives the statistics, never a packet.
 */
@Service
public class CaptureAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(CaptureAnalysisService.class);
    private static final int MAX_AI_FINDINGS = 12;
    /**
     * What the AI says with another code about something the backend's own facts already say (the same volume, the
     * same main protocol, the same drops): it is not repeated on the page.
     */
    static final Map<String, String> SAME_AS = Map.of("TRAFFIC_VOLUME", "CAPTURE_SCOPE", "TOP_PROTOCOLS", "TOP_PROTOCOL",
            "TOOL_DROPPED", "TOOL_DROPS", "STOPPED_AT_LIMIT", "STOPPED_BY_LIMIT", "UNENCRYPTED_WEB", "CLEARTEXT_TRAFFIC");

    private final TrafficCaptureRepository captures;
    private final MeasurementRepository measurements;
    private final CaptureFindingRules rules;
    private final AiAnalysisClient ai;
    private final TransactionTemplate transactions;

    public CaptureAnalysisService(TrafficCaptureRepository captures, MeasurementRepository measurements,
                                  CaptureFindingRules rules, AiAnalysisClient ai, TransactionTemplate transactions) {
        this.captures = captures;
        this.measurements = measurements;
        this.rules = rules;
        this.ai = ai;
        this.transactions = transactions;
    }

    /** Analyses (or analyses again) a capture; does nothing for one that is not completed. */
    public void analyze(Long captureId) {
        AiTrafficRequest request = transactions.execute(status -> captures.findById(captureId)
                .filter(c -> c.getStatus() == CaptureStatus.COMPLETED)
                .map(this::request)
                .orElse(null));
        if (request == null) {
            return;
        }
        List<CaptureFinding> fromAi = null;
        try {
            fromAi = parse(ai.trafficAnalyze(request));
        } catch (RuntimeException e) {
            log.warn("Capture {} analysed without the AI: {}", captureId, e.getMessage());
        }
        List<CaptureFinding> aiFindings = fromAi;
        transactions.executeWithoutResult(status -> captures.findById(captureId).ifPresent(capture -> {
            List<CaptureFinding> findings = new ArrayList<>(rules.facts(capture));
            if (aiFindings == null) {
                findings.addAll(rules.fallbackInferences(capture));
            } else {
                Set<String> codes = new HashSet<>();
                findings.forEach(f -> codes.add(f.getCode()));
                aiFindings.stream()
                        .filter(f -> !codes.contains(SAME_AS.getOrDefault(f.getCode(), f.getCode())))
                        .filter(f -> codes.add(f.getCode()))
                        .forEach(findings::add);
            }
            capture.analyzed(aiFindings == null ? FindingSource.RULES : FindingSource.AI, findings);
            captures.save(capture);
        }));
    }

    AiTrafficRequest request(TrafficCapture c) {
        Double loss = measurements.findByNetworkIdOrderByMeasuredAtDesc(c.getNetwork().getId(), PageRequest.of(0, 1))
                .stream().findFirst().map(Measurement::getPacketLossPct).orElse(null);
        return new AiTrafficRequest(c.getDurationMs() == null ? c.getMaxSeconds() : c.getDurationMs() / 1000.0,
                c.getPacketCount(), c.getByteCount(), c.getDroppedByTool(), c.getMalformedPackets(),
                c.getInboundBytes(), c.getOutboundBytes(), c.getBroadcastPackets(), c.getMulticastPackets(),
                c.getDistinctLocalHosts(), c.getDistinctRemoteHosts(),
                c.getStopReason() == null ? null : c.getStopReason().name(), loss,
                c.getProtocols().stream()
                        .map(p -> new AiTrafficRequest.Protocol(p.getProtocol(), p.getPackets(), p.getBytes())).toList(),
                c.getPorts().stream()
                        .map(p -> new AiTrafficRequest.Port(p.getTransport(), p.getPort(), p.getPackets(), p.getBytes()))
                        .toList(),
                c.getIntervals().stream()
                        .map(i -> new AiTrafficRequest.Interval(i.getSecond(), i.getPackets(), i.getBytes())).toList());
    }

    /**
     * The AI's answer: {@code facts} (observations) and {@code inferences} (with confidence). Anything malformed is
     * skipped, never turned into a finding; an inference without a confidence is not accepted as one.
     */
    static List<CaptureFinding> parse(JsonNode answer) {
        if (answer == null || !answer.isObject()) {
            throw new IllegalStateException("La IA respondió algo que no es un análisis.");
        }
        List<CaptureFinding> findings = new ArrayList<>();
        for (JsonNode fact : answer.path("facts")) {
            try {
                findings.add(CaptureFinding.observed(fact.path("code").asText(), severity(fact),
                        fact.path("title").asText(), fact.path("evidence").asText(),
                        text(fact, "recommendation"), FindingSource.AI));
            } catch (IllegalArgumentException e) {
                log.debug("AI fact skipped: {}", e.getMessage());
            }
        }
        for (JsonNode inference : answer.path("inferences")) {
            if (!inference.path("confidence").isNumber()) {
                continue;
            }
            try {
                // an inference is never CRITICAL: that level is kept for what was measured
                FindingSeverity severity = severity(inference) == FindingSeverity.CRITICAL ? FindingSeverity.WARNING
                        : severity(inference);
                findings.add(CaptureFinding.inferred(inference.path("code").asText(), severity,
                        inference.path("title").asText(), inference.path("evidence").asText(),
                        text(inference, "recommendation"), inference.path("confidence").asDouble(), FindingSource.AI));
            } catch (IllegalArgumentException e) {
                log.debug("AI inference skipped: {}", e.getMessage());
            }
        }
        return findings.size() > MAX_AI_FINDINGS ? findings.subList(0, MAX_AI_FINDINGS) : findings;
    }

    private static FindingSeverity severity(JsonNode node) {
        try {
            return FindingSeverity.valueOf(node.path("severity").asText("INFO"));
        } catch (IllegalArgumentException e) {
            return FindingSeverity.INFO;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
