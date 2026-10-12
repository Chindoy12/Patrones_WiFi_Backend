package com.wifisense.service;

import com.wifisense.analysis.AiAnalysisClient;
import com.wifisense.dto.AiModelOverview;
import org.springframework.stereotype.Service;

@Service
public class AiModelService {

    private final AiAnalysisClient aiClient;

    public AiModelService(AiAnalysisClient aiClient) {
        this.aiClient = aiClient;
    }

    public AiModelOverview overview() {
        var model = aiClient.modelInfo();
        var evaluation = aiClient.modelEvaluation();
        return new AiModelOverview(model, evaluation.orElse(null), evaluation.isPresent());
    }
}
