package com.wifisense.repository;

import com.wifisense.model.AgentKey;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AgentKeyRepository extends JpaRepository<AgentKey, Long> {

    /** The agent authenticates with the SHA-256 of its key; the network comes along to avoid a second query. */
    @EntityGraph(attributePaths = "network")
    Optional<AgentKey> findByKeyHash(String keyHash);

    Optional<AgentKey> findByNetworkId(Long networkId);

    @Modifying
    @Query("update AgentKey k set k.lastSeenAt = :at where k.id = :id")
    int markSeen(@Param("id") Long id, @Param("at") Instant at);

    @Modifying
    @Query("update AgentKey k set k.identifyUntil = :until where k.network.id = :networkId")
    int identifyUntil(@Param("networkId") Long networkId, @Param("until") Instant until);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AgentKey k where k.network.id = :networkId")
    int deleteByNetworkId(@Param("networkId") Long networkId);
}
