package com.comercialhibrido.repository;

import com.comercialhibrido.domain.entity.InboundMessageJob;
import com.comercialhibrido.domain.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
