package com.wifisense.repository;

import com.wifisense.model.HomeNetwork;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * The end-user queries are scoped to the owner's username, so one user can never reach another user's networks. Only
 * {@link #findAllByOrderByCreatedAtAsc()} reads every network, for the team panel (ADMIN and ANALYST).
 */
public interface HomeNetworkRepository extends JpaRepository<HomeNetwork, Long> {

    List<HomeNetwork> findByOwner_UsernameOrderByCreatedAtAsc(String username);

    Optional<HomeNetwork> findByIdAndOwner_Username(Long id, String username);

    long countByOwner_Username(String username);

    boolean existsByOwner_UsernameAndNameIgnoreCase(String username, String name);

    /** Every home network with its owner, oldest first: the team panel shows real data, not the simulated one. */
    @EntityGraph(attributePaths = "owner")
    List<HomeNetwork> findAllByOrderByCreatedAtAsc();
}
