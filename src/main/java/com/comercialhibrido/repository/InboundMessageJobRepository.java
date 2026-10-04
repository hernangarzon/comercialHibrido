package com.comercialhibrido.repository;

import com.comercialhibrido.domain.entity.InboundMessageJob;
import com.comercialhibrido.domain.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface InboundMessageJobRepository extends JpaRepository<InboundMessageJob, UUID> {

    boolean existsByWhatsappMessageId(String whatsappMessageId);

    List<InboundMessageJob> findTop10ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
        JobStatus status,
        Instant now
    );

    List<InboundMessageJob> findTop10ByStatusAndLockedAtBeforeOrderByCreatedAtAsc(
        JobStatus status,
        Instant before
    );

    /**
     * Reclama el job de forma atómica: solo una transacción logra pasar de PENDING
     * a PROCESSING, aunque haya varias instancias del worker. Devuelve 1 si lo ganó.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE InboundMessageJob j
           SET j.status = com.comercialhibrido.domain.enums.JobStatus.PROCESSING,
               j.lockedAt = :now, j.updatedAt = :now, j.version = COALESCE(j.version, 0) + 1
         WHERE j.id = :id AND j.status = com.comercialhibrido.domain.enums.JobStatus.PENDING
        """)
    int claim(@Param("id") UUID id, @Param("now") Instant now);
}
