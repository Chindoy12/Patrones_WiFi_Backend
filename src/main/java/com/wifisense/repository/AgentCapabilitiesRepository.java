package com.wifisense.repository;

import com.wifisense.model.AgentCapabilities;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentCapabilitiesRepository extends JpaRepository<AgentCapabilities, Long> {

    Optional<AgentCapabilities> findByNetworkId(Long networkId);

    Optional<AgentCapabilities> findByHomeNetworkId(Long homeNetworkId);
}
