package com.wifisense.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifisense.analysis.AiAnalysisClient;
import com.wifisense.analysis.AiQualityRequest;
import com.wifisense.analysis.AiServiceUnavailableException;
import com.wifisense.dto.ConnectionTestRequest;
import com.wifisense.dto.ConnectionTestResponse;
import com.wifisense.model.ConnectionTest;
import com.wifisense.model.HomeNetwork;
import com.wifisense.repository.ConnectionTestRepository;
import com.wifisense.repository.HomeNetworkRepository;
import com.wifisense.speedtest.SpeedTestThrottle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Saves the connection tests of end users and asks the AI to grade them. A network of someone else answers like
 * one that does not exist. If the AI is down the test is still saved, without a grade.
 */
@Service
public class ConnectionTestService {

    static final int MAX_HISTORY = 30;
    private static final Set<String> GRADES = Set.of("EXCELLENT", "GOOD", "FAIR", "POOR");
    private static final Logger log = LoggerFactory.getLogger(ConnectionTestService.class);

    private final ConnectionTestRepository tests;
    private final HomeNetworkRepository networks;
    private final AiAnalysisClient aiClient;
    private final SpeedTestThrottle throttle;
    private final ObjectMapper json;

    public ConnectionTestService(ConnectionTestRepository tests, HomeNetworkRepository networks,
                                 AiAnalysisClient aiClient, SpeedTestThrottle throttle, ObjectMapper json) {
        this.tests = tests;
        this.networks = networks;
        this.aiClient = aiClient;
        this.throttle = throttle;
        this.json = json;
    }

    @Transactional
    public ConnectionTestResponse save(String username, Long networkId, ConnectionTestRequest request) {
        HomeNetwork network = ownNetwork(username, networkId);
        throttle.acquireTest(username);
        ConnectionTest test = new ConnectionTest(network, request.latencyMs(), request.jitterMs(),
                request.downloadMbps(), request.uploadMbps());
        grade(test, network);
        return toResponse(tests.save(test));
    }

    @Transactional(readOnly = true)
    public List<ConnectionTestResponse> history(String username, Long networkId, int limit) {
        ownNetwork(username, networkId);
        int size = Math.max(1, Math.min(limit, MAX_HISTORY));
        return tests.findByNetworkIdOrderByCreatedAtDesc(networkId, PageRequest.of(0, size)).stream()
                .map(this::toResponse).toList();
    }

    private HomeNetwork ownNetwork(String username, Long networkId) {
        return networks.findByIdAndOwner_Username(networkId, username)
                .orElseThrow(() -> new ResourceNotFoundException("la red", networkId));
    }

    private void grade(ConnectionTest test, HomeNetwork network) {
        Double contracted = network.getContractedMbps() == null ? null : network.getContractedMbps().doubleValue();
        try {
            JsonNode quality = aiClient.qualityScore(new AiQualityRequest(test.getLatencyMs(), test.getJitterMs(),
                    null, test.getDownloadMbps(), test.getUploadMbps(), contracted, null));
            int score = quality.path("score").asInt(-1);
            String grade = quality.path("grade").asText();
            if (score < 0 || score > 100 || !GRADES.contains(grade)) {
                log.warn("Ignoring unexpected quality answer from the AI service");
                return;
            }
            test.grade(score, grade, json.writeValueAsString(quality));
        } catch (AiServiceUnavailableException | JsonProcessingException e) {
            log.warn("Connection test saved without grade: {}", e.getMessage());
        }
    }

    private ConnectionTestResponse toResponse(ConnectionTest test) {
        return new ConnectionTestResponse(test.getId(), test.getNetwork().getId(), test.getLatencyMs(),
                test.getJitterMs(), test.getDownloadMbps(), test.getUploadMbps(), test.getQualityScore(),
                test.getQualityGrade(), details(test), test.getCreatedAt());
    }

    private JsonNode details(ConnectionTest test) {
        if (test.getQualityDetails() == null) {
            return null;
        }
        try {
            return json.readTree(test.getQualityDetails());
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
