package com.wifisense.service;

import com.wifisense.dto.AgentCaptureResult;
import com.wifisense.model.CaptureFinding;
import com.wifisense.model.FindingCategory;
import com.wifisense.model.FindingSeverity;
import com.wifisense.model.FindingSource;
import com.wifisense.model.TrafficCapture;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static com.wifisense.service.CaptureFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

class CaptureFindingRulesTest {

    private final CaptureFindingRules rules = new CaptureFindingRules();
    private final CaptureRecorder recorder = new CaptureRecorder();

    private TrafficCapture completed(AgentCaptureResult result) {
        TrafficCapture capture = CaptureFixtures.running(CaptureFixtures.network(4L), result.captureId(),
                NOW.minusSeconds(31));
        recorder.apply(capture, result, NOW);
        return capture;
    }

    private static AgentCaptureResult result(int packets, int broadcast, Integer dropped,
                                             List<AgentCaptureResult.Protocol> protocols,
                                             List<AgentCaptureResult.Port> ports,
                                             List<AgentCaptureResult.Interval> intervals, String stop) {
        return new AgentCaptureResult(7L, "COMPLETED", null, stop, "NPCAP", "Ethernet", "2.0.0", 30_000, packets,
                packets * 100L, dropped, 0, 0L, 0L, broadcast, 0, 3, 5, protocols, ports, intervals);
    }

    private static CaptureFinding code(List<CaptureFinding> findings, String code) {
        return findings.stream().filter(f -> f.getCode().equals(code)).findFirst().orElseThrow();
    }

    @Test
    void everyFactIsAnObservationOfTheRulesWithItsNumbers() {
        List<CaptureFinding> facts = rules.facts(completed(CaptureFixtures.completed(7L)));

        assertThat(facts).allSatisfy(f -> {
            assertThat(f.getCategory()).isEqualTo(FindingCategory.OBSERVED);
            assertThat(f.getSource()).isEqualTo(FindingSource.RULES);
            assertThat(f.getConfidence()).isNull();
        });
        assertThat(code(facts, "CAPTURE_SCOPE").getEvidence()).contains("1200 paquetes").contains("30.2 s");
        assertThat(code(facts, "TOP_PROTOCOL").getTitle()).endsWith("HTTPS");
        assertThat(code(facts, "TOP_PROTOCOL").getEvidence()).contains("900 de 1200").contains("75.0 %");
        assertThat(code(facts, "BROADCAST_SHARE").getEvidence()).contains("120 de 1200");
        assertThat(code(facts, "TOOL_DROPS").getTitle()).contains("no perdió");
    }

    @Test
    void anEmptyCaptureSaysSoAndHowToCheckIt() {
        List<CaptureFinding> facts = rules.facts(completed(result(0, 0, null, List.of(), List.of(), List.of(),
                "DURATION")));

        assertThat(facts).extracting(CaptureFinding::getCode).containsExactly("CAPTURE_SCOPE", "EMPTY_CAPTURE");
        assertThat(code(facts, "EMPTY_CAPTURE").getSeverity()).isEqualTo(FindingSeverity.WARNING);
        assertThat(code(facts, "EMPTY_CAPTURE").getRecommendation()).contains("interfaz");
    }

    @Test
    void packetsDroppedByTheToolAreNeverCalledNetworkLoss() {
        CaptureFinding drops = code(rules.facts(completed(result(100, 0, 20, List.of(), List.of(), List.of(),
                "DURATION"))), "TOOL_DROPS");

        assertThat(drops.getSeverity()).isEqualTo(FindingSeverity.WARNING);
        assertThat(drops.getEvidence()).contains("20 paquetes").contains("No es pérdida de la red");
    }

    @Test
    void aToolThatDoesNotReportDropsGetsNoFactAboutThem() {
        assertThat(rules.facts(completed(result(100, 0, null, List.of(), List.of(), List.of(), "DURATION"))))
                .extracting(CaptureFinding::getCode).doesNotContain("TOOL_DROPS");
    }

    @Test
    void muchBroadcastIsAWarning() {
        assertThat(code(rules.facts(completed(result(100, 40, 0, List.of(), List.of(), List.of(), "DURATION"))),
                "BROADCAST_SHARE").getSeverity()).isEqualTo(FindingSeverity.WARNING);
    }

    @Test
    void cleartextProtocolsAreReported() {
        List<CaptureFinding> facts = rules.facts(completed(result(100, 0, 0,
                List.of(new AgentCaptureResult.Protocol("HTTP", 10, 1000L)),
                List.of(new AgentCaptureResult.Port("TCP", 23, 5, 500L)), List.of(), "DURATION")));

        assertThat(code(facts, "CLEARTEXT_TRAFFIC").getEvidence()).contains("Al menos 15 paquetes");
    }

    @Test
    void aCaptureStoppedByItsLimitSaysItDoesNotCoverTheWholeDuration() {
        assertThat(rules.facts(completed(result(100, 0, 0, List.of(), List.of(), List.of(), "PACKET_LIMIT"))))
                .extracting(CaptureFinding::getCode).contains("STOPPED_BY_LIMIT");
    }

    @Test
    void withoutTheAiASecondFarAboveTheOthersIsAModerateInference() {
        List<AgentCaptureResult.Interval> intervals = new ArrayList<>(IntStream.range(0, 29)
                .mapToObj(s -> new AgentCaptureResult.Interval(s, 10, 1000L)).toList());
        intervals.add(new AgentCaptureResult.Interval(29, 200, 20_000L));

        List<CaptureFinding> inferred = rules.fallbackInferences(completed(result(490, 0, 0, List.of(), List.of(),
                intervals, "DURATION")));

        assertThat(inferred).singleElement().satisfies(f -> {
            assertThat(f.getCode()).isEqualTo("TRAFFIC_BURST");
            assertThat(f.getCategory()).isEqualTo(FindingCategory.INFERRED);
            assertThat(f.getConfidence()).isEqualByComparingTo("0.50");
            assertThat(f.getEvidence()).contains("segundo 29").contains("20.0 veces");
        });
    }

    @Test
    void evenTrafficOrTooFewSecondsInferNothing() {
        assertThat(rules.fallbackInferences(completed(CaptureFixtures.completed(7L)))).isEmpty();
        assertThat(rules.fallbackInferences(completed(result(300, 0, 0, List.of(), List.of(),
                List.of(new AgentCaptureResult.Interval(0, 290, 1000L), new AgentCaptureResult.Interval(1, 10, 10L)),
                "DURATION")))).isEmpty();
    }
}
