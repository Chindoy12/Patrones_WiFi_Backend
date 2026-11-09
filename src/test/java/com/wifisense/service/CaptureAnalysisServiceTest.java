package com.wifisense.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifisense.analysis.AiAnalysisClient;
import com.wifisense.analysis.AiTrafficRequest;
import com.wifisense.model.CaptureFinding;
import com.wifisense.model.FindingSeverity;
import com.wifisense.model.FindingSource;
import com.wifisense.model.TrafficCapture;
import com.wifisense.repository.MeasurementRepository;
import com.wifisense.repository.TrafficCaptureRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CaptureAnalysisServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final TrafficCaptureRepository captures = mock(TrafficCaptureRepository.class);
    private final MeasurementRepository measurements = mock(MeasurementRepository.class);
    private final AiAnalysisClient ai = mock(AiAnalysisClient.class);
    private final CaptureAnalysisService service = new CaptureAnalysisService(captures, measurements,
            new CaptureFindingRules(), ai, new TransactionTemplate(mock(PlatformTransactionManager.class)));

    @Test
    void theAiReceivesOnlyStatisticsAndItsInferencesAreKeptApartFromTheFacts() throws Exception {
        TrafficCapture capture = completed();
        when(ai.trafficAnalyze(any())).thenReturn(JSON.readTree("""
                {"facts": [{"code": "AI_FACT", "severity": "INFO", "title": "Tráfico cifrado",
                            "evidence": "900 de 1200 paquetes son HTTPS."}],
                 "inferences": [{"code": "AI_GUESS", "severity": "WARNING", "title": "Posible ráfaga",
                                 "evidence": "Un segundo con el doble de paquetes.", "confidence": 0.4,
                                 "recommendation": "Repite la captura en otro horario."}]}"""));

        service.analyze(7L);

        ArgumentCaptor<AiTrafficRequest> sent = ArgumentCaptor.forClass(AiTrafficRequest.class);
        verify(ai).trafficAnalyze(sent.capture());
        assertThat(JSON.writeValueAsString(sent.getValue())).doesNotContain("192.168").doesNotContain("purpose");
        assertThat(capture.getAnalyzedWith()).isEqualTo(FindingSource.AI);
        CaptureFinding guess = find(capture, "AI_GUESS");
        assertThat(guess.getConfidence()).isNotNull();
        assertThat(find(capture, "AI_FACT").getConfidence()).isNull();
        assertThat(capture.getFindings()).anyMatch(f -> f.getSource() == FindingSource.RULES);
        verify(captures).save(capture);
    }

    @Test
    void whatTheBackendAlreadySaysIsNotRepeatedByTheAi() throws Exception {
        TrafficCapture capture = completed();
        when(ai.trafficAnalyze(any())).thenReturn(JSON.readTree("""
                {"facts": [{"code": "TRAFFIC_VOLUME", "title": "Volumen", "evidence": "1200 paquetes."},
                           {"code": "TOP_PROTOCOLS", "title": "Protocolos", "evidence": "HTTPS 75 %."},
                           {"code": "CONNECTION_LOSS", "title": "Pérdida de la prueba", "evidence": "0.4 %."}],
                 "inferences": []}"""));

        service.analyze(7L);

        assertThat(capture.getFindings()).extracting(CaptureFinding::getCode)
                .contains("CAPTURE_SCOPE", "TOP_PROTOCOL", "CONNECTION_LOSS")
                .doesNotContain("TRAFFIC_VOLUME", "TOP_PROTOCOLS");
    }

    @Test
    void withoutTheAiTheBackendsOwnRulesAnalyseAndTheCaptureSaysSo() {
        TrafficCapture capture = completed();
        when(ai.trafficAnalyze(any())).thenThrow(new IllegalStateException("IA apagada"));

        service.analyze(7L);

        assertThat(capture.getAnalyzedWith()).isEqualTo(FindingSource.RULES);
        assertThat(capture.getFindings()).isNotEmpty().allMatch(f -> f.getSource() == FindingSource.RULES);
    }

    @Test
    void aCaptureThatDidNotCompleteIsNotAnalysed() {
        TrafficCapture pending = CaptureFixtures.pending(CaptureFixtures.network(4), 8L, CaptureFixtures.NOW);
        when(captures.findById(8L)).thenReturn(Optional.of(pending));

        service.analyze(8L);

        verifyNoInteractions(ai);
        verify(captures, never()).save(any());
    }

    @Test
    void anInferenceWithoutConfidenceOrAMalformedFindingIsDiscardedAndCriticalIsOnlyForFacts() throws Exception {
        List<CaptureFinding> parsed = CaptureAnalysisService.parse(JSON.readTree("""
                {"facts": [{"code": "", "title": "Sin código"}],
                 "inferences": [{"code": "NO_CONFIDENCE", "title": "Sin confianza", "evidence": "x"},
                                {"code": "TOO_SURE", "severity": "CRITICAL", "title": "Un equipo es malicioso",
                                 "evidence": "Muchos paquetes ARP.", "confidence": 0.9}]}"""));

        assertThat(parsed).extracting(CaptureFinding::getCode).containsExactly("TOO_SURE");
        assertThat(parsed.getFirst().getSeverity()).isEqualTo(FindingSeverity.WARNING);
        assertThatThrownBy(() -> CaptureAnalysisService.parse(JSON.readTree("[1, 2]")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theAiCannotFloodTheCaptureWithFindings() throws Exception {
        StringBuilder many = new StringBuilder("{\"facts\": [");
        for (int i = 0; i < 30; i++) {
            many.append(i == 0 ? "" : ",").append("{\"code\": \"F").append(i)
                    .append("\", \"title\": \"Hecho\", \"evidence\": \"Contado\"}");
        }
        JsonNode answer = JSON.readTree(many.append("]}").toString());

        assertThat(CaptureAnalysisService.parse(answer)).hasSize(12);
    }

    private TrafficCapture completed() {
        TrafficCapture capture = CaptureFixtures.running(CaptureFixtures.network(4), 7L, CaptureFixtures.NOW);
        new CaptureRecorder().apply(capture, CaptureFixtures.completed(7L), CaptureFixtures.NOW.plusSeconds(31));
        when(captures.findById(7L)).thenReturn(Optional.of(capture));
        return capture;
    }

    private static CaptureFinding find(TrafficCapture capture, String code) {
        return capture.getFindings().stream().filter(f -> f.getCode().equals(code)).findFirst().orElseThrow();
    }
}
