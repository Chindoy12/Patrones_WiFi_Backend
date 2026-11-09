package com.wifisense.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * <b>Adapter</b> between the Java code and the REST API of the AI service (Python/FastAPI): it turns each method of
 * {@link AiAnalysisClient} into its HTTP call (path, X-API-Key header, JSON, timeouts) and every HTTP or network error
 * into {@link AiServiceUnavailableException}, so the rest of the backend never sees HTTP details. In the application
 * it is wrapped by {@link CircuitBreakerAiClient}.
 */
@Component
public class HttpAiAnalysisClient implements AiAnalysisClient {

    private static final Logger log = LoggerFactory.getLogger(HttpAiAnalysisClient.class);

    private static final String DETECT_PATH = "/api/v1/anomalies/detect";
    private static final String MODEL_PATH = "/api/v1/model";
    private static final String EVALUATION_PATH = "/api/v1/model/evaluation";
    private static final String QUALITY_PATH = "/api/v1/quality/score";
    private static final String SECURITY_PATH = "/api/v1/security/assess";
    private static final String CHANNEL_PATH = "/api/v1/channel/recommend";
    private static final String TRAFFIC_PATH = "/api/v1/traffic/analyze";
    private static final String NEARBY_PATH = "/api/v1/nearby/analyze";
    private static final String HEALTH_PATH = "/health";

    private static final String TRAIN_PATH = "/api/v1/model/train";
    private static final Duration TRAINING_TIMEOUT = Duration.ofMinutes(2);

    private final RestClient restClient;
    private final RestClient trainingClient;

    public HttpAiAnalysisClient(RestClient.Builder builder, AiServiceProperties properties) {
        if (!StringUtils.hasText(properties.apiKey()) && !isLocal(properties.url())) {
            // the AI service in Railway refuses to start without a key; this makes the cause visible on this side too
            log.warn("AI_API_KEY is not set but the AI service is at {}: its calls will be rejected", properties.url());
        }
        this.restClient = client(builder.clone(), properties, properties.timeout());
        // Training takes a few seconds per thousand measurements: its own client waits longer.
        this.trainingClient = client(builder.clone(), properties, TRAINING_TIMEOUT);
    }

    static boolean isLocal(String url) {
        String host = url == null ? "" : java.net.URI.create(url).getHost();
        return host == null || host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1");
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
    public JsonNode trafficAnalyze(AiTrafficRequest request) {
        try {
            return restClient.post()
                    .uri(TRAFFIC_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new AiServiceUnavailableException("Falló el análisis de tráfico: " + e.getMessage(), e);
        }
    }

    @Override
    public JsonNode nearbyAnalyze(AiNearbyRequest request) {
        try {
            return restClient.post()
                    .uri(NEARBY_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new AiServiceUnavailableException("Falló el análisis de redes cercanas: " + e.getMessage(), e);
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
