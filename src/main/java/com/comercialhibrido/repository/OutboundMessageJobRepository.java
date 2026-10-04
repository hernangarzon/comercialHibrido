package com.comercialhibrido.repository;

import com.comercialhibrido.domain.entity.OutboundMessageJob;
import com.comercialhibrido.domain.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboundMessageJobRepository extends JpaRepository<OutboundMessageJob, UUID> {

    List<OutboundMessageJob> findTop10ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
        JobStatus status,
        Instant now
    );

    List<OutboundMessageJob> findTop10ByStatusAndLockedAtBeforeOrderByCreatedAtAsc(
        JobStatus status,
        Instant before
    );

    Optional<OutboundMessageJob> findByWhatsappMessageId(String whatsappMessageId);

    /** Igual que InboundMessageJobRepository.claim: PENDING -> PROCESSING atómico. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE OutboundMessageJob j
           SET j.status = com.comercialhibrido.domain.enums.JobStatus.PROCESSING,
               j.lockedAt = :now, j.updatedAt = :now, j.version = COALESCE(j.version, 0) + 1
         WHERE j.id = :id AND j.status = com.comercialhibrido.domain.enums.JobStatus.PENDING
        """)
    int claim(@Param("id") UUID id, @Param("now") Instant now);
}
