package com.wifisense.repository;

import com.wifisense.model.AgentPairingCode;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AgentPairingCodeRepository extends JpaRepository<AgentPairingCode, Long> {

    @EntityGraph(attributePaths = "network")
    Optional<AgentPairingCode> findByCodeHash(String codeHash);

    @Modifying(flushAutomatically = true)
    @Query("delete from AgentPairingCode c where c.network.id = :networkId")
    int deleteByNetworkId(@Param("networkId") Long networkId);

    @Modifying
    @Query("delete from AgentPairingCode c where c.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
