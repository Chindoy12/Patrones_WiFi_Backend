package com.wifisense.repository;

import com.wifisense.model.SensorDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SensorDeviceRepository extends JpaRepository<SensorDevice, SensorDevice.Key> {

    List<SensorDevice> findByNetworkIdOrderByFirstSeenAtAsc(Long networkId);

    Optional<SensorDevice> findByNetworkIdAndFingerprint(Long networkId, String fingerprint);

    boolean existsByNetworkId(Long networkId);

    @Modifying
    @Query("delete from SensorDevice d where d.lastSeenAt < :before")
    int deleteNotSeenSince(@Param("before") Instant before);
}
