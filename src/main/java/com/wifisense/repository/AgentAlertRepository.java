package com.wifisense.repository;

import com.wifisense.model.AgentAlert;
import com.wifisense.model.AlertType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AgentAlertRepository extends JpaRepository<AgentAlert, Long> {

    List<AgentAlert> findByNetworkIdOrderByCreatedAtDesc(Long networkId, Pageable pageable);

    /** Whether an email of this type was already sent for this network since {@code since} (to avoid repeats). */
    boolean existsByNetworkIdAndTypeAndEmailedTrueAndCreatedAtAfter(Long networkId, AlertType type, Instant since);

    @Modifying
    @Query("delete from AgentAlert a where a.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
