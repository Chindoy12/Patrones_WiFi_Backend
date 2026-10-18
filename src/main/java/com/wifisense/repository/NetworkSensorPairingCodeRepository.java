package com.wifisense.repository;

import com.wifisense.model.NetworkSensorPairingCode;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface NetworkSensorPairingCodeRepository extends JpaRepository<NetworkSensorPairingCode, Long> {

    @EntityGraph(attributePaths = "network")
    Optional<NetworkSensorPairingCode> findByCodeHash(String codeHash);

    Optional<NetworkSensorPairingCode> findByNetworkId(Long networkId);

    @Modifying(flushAutomatically = true)
    @Query("delete from NetworkSensorPairingCode c where c.network.id = :networkId")
    int deleteByNetworkId(@Param("networkId") Long networkId);

    @Modifying
    @Query("delete from NetworkSensorPairingCode c where c.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
