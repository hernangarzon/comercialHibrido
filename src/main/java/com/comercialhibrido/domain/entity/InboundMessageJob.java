package com.comercialhibrido.domain.entity;

import com.comercialhibrido.domain.enums.JobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inbound_message_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundMessageJob {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String whatsappMessageId;

    @Column(nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    private Instant lockedAt;

    @Column(length = 2000)
    private String lastError;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (nextAttemptAt == null) {
            nextAttemptAt = now;
        }
    }

    public void markProcessing() {
        status = JobStatus.PROCESSING;
        lockedAt = Instant.now();
        updatedAt = Instant.now();
    }

    public void markCompleted() {
        status = JobStatus.COMPLETED;
        lockedAt = null;
        lastError = null;
        updatedAt = Instant.now();
    }

    public void markRetry(Instant nextAttempt, String error, boolean exhausted) {
        attempts = attempts + 1;
        status = exhausted ? JobStatus.FAILED : JobStatus.PENDING;
        nextAttemptAt = nextAttempt;
        lockedAt = null;
        lastError = error == null ? null : error.substring(0, Math.min(error.length(), 2000));
        updatedAt = Instant.now();
    }
}
