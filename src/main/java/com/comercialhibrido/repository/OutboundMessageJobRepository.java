package com.comercialhibrido.repository;

import com.comercialhibrido.domain.entity.OutboundMessageJob;
import com.comercialhibrido.domain.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
