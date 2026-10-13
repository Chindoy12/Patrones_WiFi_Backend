package com.wifisense.repository;

import com.wifisense.model.AiPrediction;
import com.wifisense.model.AnomalyType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AiPredictionRepository extends JpaRepository<AiPrediction, Long> {

    @EntityGraph(attributePaths = "network")
    List<AiPrediction> findByAnomalyDetectedTrueOrderByCreatedAtDesc(Pageable pageable);

    List<AiPrediction> findByAnalysisResultIdIn(Collection<Long> analysisResultIds);

    long countByAnomalyDetectedTrueAndCreatedAtAfter(Instant since);

    long countByNetworkIdAndAnomalyDetectedTrueAndCreatedAtBetween(Long networkId, Instant from, Instant to);

    interface CauseCount {
        AnomalyType getAnomalyType();

        long getTotal();
    }

    @Query("""
            select p.anomalyType as anomalyType, count(p) as total from AiPrediction p
            where p.anomalyDetected = true and p.createdAt >= :since
            group by p.anomalyType order by count(p) desc""")
    List<CauseCount> countAnomaliesByCauseSince(@Param("since") Instant since);
}
