package com.wifisense.repository;

import com.wifisense.model.HomeNetwork;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Every query is scoped to the owner's username, so one user can never reach another user's networks. */
public interface HomeNetworkRepository extends JpaRepository<HomeNetwork, Long> {

    List<HomeNetwork> findByOwner_UsernameOrderByCreatedAtAsc(String username);

    Optional<HomeNetwork> findByIdAndOwner_Username(Long id, String username);

    long countByOwner_Username(String username);

    boolean existsByOwner_UsernameAndNameIgnoreCase(String username, String name);
}
