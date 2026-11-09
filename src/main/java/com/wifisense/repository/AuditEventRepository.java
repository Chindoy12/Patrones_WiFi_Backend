package com.wifisense.repository;

import com.wifisense.model.AuditAction;
import com.wifisense.model.AuditEvent;
import com.wifisense.model.AuditOutcome;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /** The audit page: newest first, optionally only one action and/or one outcome. */
    @Query("select e from AuditEvent e where (:action is null or e.action = :action) "
            + "and (:outcome is null or e.outcome = :outcome) order by e.occurredAt desc, e.id desc")
    Page<AuditEvent> search(@Param("action") AuditAction action, @Param("outcome") AuditOutcome outcome,
                            Pageable pageable);

    /**
     * Nightly retention. Native: the entity is immutable for Hibernate, and the table itself refuses to delete
     * anything of the last 30 days.
     */
    @Modifying
    @Query(value = "DELETE FROM audit_events WHERE occurred_at < :before", nativeQuery = true)
    int deleteOlderThan(@Param("before") Instant before);
}
