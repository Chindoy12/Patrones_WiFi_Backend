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
import java.util.Optional;

@Component
public class HttpAiAnalysisClient implements AiAnalysisClient {

    private static final String DETECT_PATH = "/api/v1/anomalies/detect";
    private static final String MODEL_PATH = "/api/v1/model";
    private static final String EVALUATION_PATH = "/api/v1/model/evaluation";

    private final RestClient restClient;

    public HttpAiAnalysisClient(RestClient.Builder builder, AiServiceProperties properties) {
        // HTTP/1.1 on purpose: over plain http (Railway's private network) the JDK client would otherwise try an h2c
        // upgrade, which uvicorn rejects and then drops the request body (422 "Field required").
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());

        this.restClient = builder
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
}
