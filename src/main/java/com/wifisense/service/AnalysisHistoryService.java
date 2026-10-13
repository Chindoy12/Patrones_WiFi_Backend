package com.wifisense.service;

import com.wifisense.dto.AiPredictionResponse;
import com.wifisense.dto.AnomalyCauseCount;
import com.wifisense.dto.AnalysisResultResponse;
import com.wifisense.model.AiPrediction;
import com.wifisense.model.AnalysisResult;
import com.wifisense.repository.AiPredictionRepository;
import com.wifisense.repository.AnalysisResultRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AnalysisHistoryService {

    static final int MAX_DAYS = 365;

    private final AnalysisResultRepository results;
    private final AiPredictionRepository predictions;
    private final Clock clock;

    public AnalysisHistoryService(AnalysisResultRepository results, AiPredictionRepository predictions, Clock clock) {
        this.results = results;
        this.predictions = predictions;
        this.clock = clock;
    }

    public List<AnalysisResultResponse> history(Long networkId, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, 200)));
        List<AnalysisResult> found = networkId == null
                ? results.findAllByOrderByCreatedAtDesc(page)
                : results.findByNetworkIdOrderByCreatedAtDesc(networkId, page);
        if (found.isEmpty()) {
            return List.of();
        }

        // One query for all predictions instead of one per result.
        Map<Long, AiPrediction> byResult = predictions
                .findByAnalysisResultIdIn(found.stream().map(AnalysisResult::getId).toList()).stream()
                .collect(Collectors.toMap(p -> p.getAnalysisResult().getId(), Function.identity()));
        return found.stream().map(r -> AnalysisResultResponse.from(r, byResult.get(r.getId()))).toList();
    }

    public List<AiPredictionResponse> anomalies(int limit) {
        return predictions.findByAnomalyDetectedTrueOrderByCreatedAtDesc(PageRequest.of(0, Math.max(1, Math.min(limit, 200))))
                .stream().map(AiPredictionResponse::from).toList();
    }

    /** Anomalies of the last `days` days grouped by probable cause, most frequent first. */
    public List<AnomalyCauseCount> anomalyCauses(int days) {
        int period = Math.max(1, Math.min(days, MAX_DAYS));
        return predictions.countAnomaliesByCauseSince(clock.instant().minus(Duration.ofDays(period))).stream()
                .map(row -> AnomalyCauseCount.of(row.getAnomalyType(), row.getTotal()))
                .toList();
    }
}
