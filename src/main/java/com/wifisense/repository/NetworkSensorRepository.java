package com.wifisense.repository;

import com.wifisense.model.NetworkSensor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface NetworkSensorRepository extends JpaRepository<NetworkSensor, Long> {

    /** The sensor that sent a report, with its network (one query, unique index on key_hash). */
    @EntityGraph(attributePaths = "network")
    Optional<NetworkSensor> findByKeyHash(String keyHash);

    Optional<NetworkSensor> findByNetworkId(Long networkId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from NetworkSensor s where s.network.id = :networkId")
    int deleteByNetworkId(@Param("networkId") Long networkId);

    @Modifying
    @Query("""
            update NetworkSensor s set s.lastSeenAt = :at, s.agentVersion = :version, s.platform = :platform
            where s.id = :id""")
    int markSeen(@Param("id") Long id, @Param("at") Instant at, @Param("version") String version,
                 @Param("platform") String platform);
}
