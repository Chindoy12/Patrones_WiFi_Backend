package com.wifisense.controller;

import com.wifisense.analysis.NetworkAnalysisFacade;
import com.wifisense.dto.AiPredictionResponse;
import com.wifisense.dto.AnalysisRequest;
import com.wifisense.dto.AnalysisResultResponse;
import com.wifisense.dto.AnomalyCauseCount;
import com.wifisense.dto.StrategyResponse;
import com.wifisense.service.AnalysisHistoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AnalysisController {

    private final NetworkAnalysisFacade analysisFacade;
    private final AnalysisHistoryService historyService;

    public AnalysisController(NetworkAnalysisFacade analysisFacade, AnalysisHistoryService historyService) {
        this.analysisFacade = analysisFacade;
        this.historyService = historyService;
    }

    @PostMapping("/networks/{networkId}/analyses")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisResultResponse analyze(@PathVariable Long networkId, @Valid @RequestBody AnalysisRequest request,
                                          Authentication authentication) {
        return analysisFacade.analyze(networkId, request.type(), authentication.getName());
    }

    @GetMapping("/analyses")
    public List<AnalysisResultResponse> history(@RequestParam(required = false) Long networkId,
                                                @RequestParam(defaultValue = "50") int limit) {
        return historyService.history(networkId, limit);
    }

    @GetMapping("/analyses/strategies")
    public List<StrategyResponse> strategies() {
        return analysisFacade.availableStrategies();
    }

    @GetMapping("/anomalies")
    public List<AiPredictionResponse> anomalies(@RequestParam(defaultValue = "50") int limit) {
        return historyService.anomalies(limit);
    }

    @GetMapping("/anomalies/causes")
    public List<AnomalyCauseCount> anomalyCauses(@RequestParam(defaultValue = "30") int days) {
        return historyService.anomalyCauses(days);
    }
}
