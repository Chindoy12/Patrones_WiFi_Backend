package com.wifisense.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

@Component
public class HttpAiAnalysisClient implements AiAnalysisClient {

    private static final String DETECT_PATH = "/api/v1/anomalies/detect";
    private static final String MODEL_PATH = "/api/v1/model";
    private static final String EVALUATION_PATH = "/api/v1/model/evaluation";
    private static final String QUALITY_PATH = "/api/v1/quality/score";
    private static final String SECURITY_PATH = "/api/v1/security/assess";
    private static final String CHANNEL_PATH = "/api/v1/channel/recommend";
    private static final String HEALTH_PATH = "/health";

    private static final String TRAIN_PATH = "/api/v1/model/train";
    private static final Duration TRAINING_TIMEOUT = Duration.ofMinutes(2);

    private final RestClient restClient;
    private final RestClient trainingClient;

    public HttpAiAnalysisClient(RestClient.Builder builder, AiServiceProperties properties) {
        this.restClient = client(builder.clone(), properties, properties.timeout());
        // Training takes a few seconds per thousand measurements: its own client waits longer.
        this.trainingClient = client(builder.clone(), properties, TRAINING_TIMEOUT);
    }

    private static RestClient client(RestClient.Builder builder, AiServiceProperties properties, Duration readTimeout) {
        // HTTP/1.1 on purpose: over plain http (Railway's private network) the JDK client would otherwise try an h2c
        // upgrade, which uvicorn rejects and then drops the request body (422 "Field required").
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return builder
                .baseUrl(properties.url())
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> {
                    if (StringUtils.hasText(properties.apiKey())) {
                        headers.set("X-API-Key", properties.apiKey());
                    }
                })
                .build();
    }

    @Override
    public AiAnalysisResponse detectAnomalies(AiAnalysisRequest request) {
        try {
            return restClient.post()
                    .uri(DETECT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiAnalysisResponse.class);
        } catch (RestClientException e) {
            throw new AiServiceUnavailableException("Falló la solicitud al servicio de IA: " + e.getMessage(), e);
        }
    }

    @Override
    public JsonNode modelInfo() {
        try {
            return restClient.get().uri(MODEL_PATH).retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw new AiServiceUnavailableException("Falló la consulta del modelo de IA: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<JsonNode> modelEvaluation() {
        try {
            return Optional.ofNullable(restClient.get().uri(EVALUATION_PATH).retrieve().body(JsonNode.class));
        } catch (HttpServerErrorException.ServiceUnavailable e) {
            return Optional.empty();  // still being computed after the AI service started
        } catch (RestClientException e) {
            throw new AiServiceUnavailableException("Falló la consulta de la evaluación del modelo: " + e.getMessage(), e);
        }
    }

    @Override
    public JsonNode qualityScore(AiQualityRequest request) {
        try {
            return restClient.post()
                    .uri(QUALITY_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new AiServiceUnavailableException("Falló la calificación de calidad: " + e.getMessage(), e);
        }
    }

    @Override
    public JsonNode securityAssess(AiSecurityRequest request) {
        try {
            return restClient.post()
                    .uri(SECURITY_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new AiServiceUnavailableException("Falló la auditoría de seguridad: " + e.getMessage(), e);
        }
    }

    @Override
    public JsonNode channelRecommend(AiChannelRequest request) {
        try {
            return restClient.post()
                    .uri(CHANNEL_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new AiServiceUnavailableException("Falló la recomendación de canal: " + e.getMessage(), e);
        }
    }

    @Override
    public JsonNode trainModel(JsonNode request) {
        try {
            return trainingClient.post()
                    .uri(TRAIN_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new AiServiceUnavailableException("Falló el entrenamiento en el servicio de IA: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isHealthy() {
        try {
            JsonNode health = restClient.get().uri(HEALTH_PATH).retrieve().body(JsonNode.class);
            return health != null && "UP".equals(health.path("status").asText());
        } catch (RestClientException e) {
            return false;
        }
    }
}
