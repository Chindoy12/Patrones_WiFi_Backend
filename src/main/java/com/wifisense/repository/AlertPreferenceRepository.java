package com.wifisense.repository;

import com.wifisense.model.AlertPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AlertPreferenceRepository extends JpaRepository<AlertPreference, Long> {

    Optional<AlertPreference> findByUserId(Long userId);
}
