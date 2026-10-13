package com.wifisense.repository;

import com.wifisense.model.ConnectionTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConnectionTestRepository extends JpaRepository<ConnectionTest, Long> {

    List<ConnectionTest> findByNetworkIdOrderByCreatedAtDesc(Long networkId, Pageable pageable);
}
