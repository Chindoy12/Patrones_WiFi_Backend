package com.wifisense.repository;

import com.wifisense.model.CaptureStatus;
import com.wifisense.model.TrafficCapture;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TrafficCaptureRepository extends JpaRepository<TrafficCapture, Long> {

    /** The capture waiting or running on a team network (there is at most one). */
    Optional<TrafficCapture> findFirstByNetworkIdAndStatusIn(Long networkId, Collection<CaptureStatus> statuses);

    /** History of a team network, newest first. */
    List<TrafficCapture> findByNetworkIdOrderByRequestedAtDesc(Long networkId, Pageable pageable);

    /** A capture of that network only: a capture of another network is "not found". */
    Optional<TrafficCapture> findByIdAndNetworkId(Long id, Long networkId);

    /** Nightly retention: the statistics and findings go with it (ON DELETE CASCADE). */
    @Modifying
    @Query("delete from TrafficCapture c where c.requestedAt < :before and c.status not in :active")
    int deleteFinishedBefore(@Param("before") Instant before, @Param("active") Collection<CaptureStatus> active);
}
