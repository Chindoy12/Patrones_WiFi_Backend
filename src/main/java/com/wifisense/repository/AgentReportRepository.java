package com.wifisense.repository;

import com.wifisense.model.AgentReport;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AgentReportRepository extends JpaRepository<AgentReport, Long> {

    List<AgentReport> findByNetworkIdOrderByReceivedAtDesc(Long networkId, Pageable pageable);

    @Modifying
    @Query("delete from AgentReport r where r.receivedAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
