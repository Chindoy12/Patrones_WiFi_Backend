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

    /** The latest report of every home network that has one (index idx_agent_reports_network_time). */
    @Query("select r from AgentReport r where r.receivedAt = "
            + "(select max(r2.receivedAt) from AgentReport r2 where r2.network = r.network)")
    List<AgentReport> findLatestOfEachNetwork();

    /** Home networks with at least one anomaly since the given time (partial index idx_agent_reports_anomalies). */
    @Query("select count(distinct r.network.id) from AgentReport r where r.anomalyDetected = true "
            + "and r.receivedAt > :since")
    long countNetworksWithAnomalySince(@Param("since") Instant since);

    @Modifying
    @Query("delete from AgentReport r where r.receivedAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
