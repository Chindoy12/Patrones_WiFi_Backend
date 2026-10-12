package com.wifisense.repository;

import com.wifisense.model.Measurement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MeasurementRepository extends JpaRepository<Measurement, Long> {

    List<Measurement> findByNetworkIdOrderByMeasuredAtDesc(Long networkId, Pageable pageable);

    List<Measurement> findByNetworkIdAndMeasuredAtBetweenOrderByMeasuredAtDesc(Long networkId, Instant from,
                                                                               Instant to, Pageable pageable);

    List<Measurement> findByNetworkIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(Long networkId, Instant from,
                                                                              Instant to);

    @Query("select avg(m.latencyMs) from Measurement m where m.measuredAt >= :since")
    Double averageLatencySince(Instant since);

    Optional<Measurement> findTopByOrderByMeasuredAtDesc();
}
