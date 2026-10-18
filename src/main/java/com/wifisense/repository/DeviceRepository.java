package com.wifisense.repository;

import com.wifisense.model.Device;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    @EntityGraph(attributePaths = "network")
    List<Device> findAllByOrderByLastSeenAtDesc(Pageable page);

    @EntityGraph(attributePaths = "network")
    List<Device> findByNetworkIdOrderByLastSeenAtDesc(Long networkId, Pageable page);

    boolean existsByMacAddress(String macAddress);
}
