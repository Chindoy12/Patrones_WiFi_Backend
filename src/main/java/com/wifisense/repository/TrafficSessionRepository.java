package com.wifisense.repository;

import com.wifisense.model.TrafficSession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrafficSessionRepository extends JpaRepository<TrafficSession, Long> {

    /** Uses idx_traffic_sessions_device_time and stops after the page. */
    List<TrafficSession> findByDeviceIdOrderByStartedAtDesc(Long deviceId, Pageable page);
}
