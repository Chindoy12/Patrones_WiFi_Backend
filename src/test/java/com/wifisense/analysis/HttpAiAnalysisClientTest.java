package com.wifisense.analysis;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Talks to a real local HTTP server that plays the role of WiFiSense-ai. */
class HttpAiAnalysisClientTest {

    private HttpServer server;
    private final AtomicReference<String> receivedKey = new AtomicReference<>();
    private final AtomicReference<String> upgradeHeader = new AtomicReference<>("not called");
    private final AtomicReference<String> detectBody = new AtomicReference<>();
    private volatile int evaluationStatus = 200;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/model/evaluation", exchange -> {
            receivedKey.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            reply(exchange, evaluationStatus, evaluationStatus == 200
                    ? "{\"productionAlgorithm\":\"IsolationForest\"}"
                    : "{\"detail\":\"La evaluación del modelo está en curso.\"}");
        });
        server.createContext("/api/v1/anomalies/detect", exchange -> {
            upgradeHeader.set(exchange.getRequestHeaders().getFirst("Upgrade"));
            detectBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(exchange, 200, """
                    {"anomalyDetected":true,"anomalyScore":0.7,"severity":"HIGH","anomalyType":"CONGESTION",
                     "diagnosis":"Causa probable: congestión de la red.","message":"m","recommendation":"r",
                     "recommendationSource":"RULES","contributingFeatures":["latency"],
                     "featureDeviations":{"latency":4.1},"anomalousSamples":1,"sampleSize":1,"imputedFeatures":[],
                     "modelVersion":"isolation-forest-1.1","simulatedData":true,"futureField":"ignored"}""");
        });
        server.createContext("/api/v1/model", exchange ->
                reply(exchange, 200, "{\"algorithm\":\"IsolationForest\",\"modelVersion\":\"isolation-forest-1.1\"}"));
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private HttpAiAnalysisClient client(String url) {
        return new HttpAiAnalysisClient(RestClient.builder(),
                new AiServiceProperties(url, "shared-key", Duration.ofSeconds(2)));
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void readsModelInfoAndEvaluationWithTheSharedKey() {
        var client = client(url());

        assertThat(client.modelInfo().get("modelVersion").asText()).isEqualTo("isolation-forest-1.1");
        assertThat(client.modelEvaluation()).hasValueSatisfying(
                report -> assertThat(report.get("productionAlgorithm").asText()).isEqualTo("IsolationForest"));
        assertThat(receivedKey.get()).isEqualTo("shared-key");
    }

    @Test
    void detectionSendsThePayloadOverHttp11AndReadsTheDiagnosis() {
        var request = new AiAnalysisRequest(3, true, java.util.List.of(new AiAnalysisRequest.Sample(
                20.0, 3.0, 0.2, 250.0, -55, null, 30, null, java.time.Instant.parse("2026-10-12T12:00:00Z"))));

        AiAnalysisResponse response = client(url()).detectAnomalies(request);

        assertThat(upgradeHeader.get()).isNull();  // no h2c upgrade: uvicorn would drop the body
        assertThat(detectBody.get()).contains("\"networkId\":3").contains("\"latency\":20.0");
        assertThat(response.anomalyType()).isEqualTo("CONGESTION");
        assertThat(response.featureDeviations()).containsEntry("latency", 4.1);
    }

    @Test
    void evaluationStillRunningIsEmptyNotAnError() {
        evaluationStatus = 503;

        assertThat(client(url()).modelEvaluation()).isEmpty();
    }

    @Test
    void unreachableServiceIsReportedAsUnavailable() {
        String closed = url();
        server.stop(0);

        assertThatThrownBy(() -> client(closed).modelInfo()).isInstanceOf(AiServiceUnavailableException.class);
    }
}
