package com.wifisense.repository;

import com.wifisense.model.AgentDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AgentDeviceRepository extends JpaRepository<AgentDevice, AgentDevice.Key> {

    List<AgentDevice> findByNetworkIdOrderByFirstSeenAtAsc(Long networkId);

    Optional<AgentDevice> findByNetworkIdAndFingerprint(Long networkId, String fingerprint);

    boolean existsByNetworkId(Long networkId);

    @Modifying
    @Query("delete from AgentDevice d where d.lastSeenAt < :before")
    int deleteNotSeenSince(@Param("before") Instant before);
}
