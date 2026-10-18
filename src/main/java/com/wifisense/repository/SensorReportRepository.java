package com.wifisense.repository;

import com.wifisense.model.SensorReport;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SensorReportRepository extends JpaRepository<SensorReport, Long> {

    /** Latest reports of a team network (index idx_network_sensor_reports_network_time). */
    List<SensorReport> findByNetworkIdOrderByReceivedAtDesc(Long networkId, Pageable pageable);

    @Modifying
    @Query("delete from SensorReport r where r.receivedAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
