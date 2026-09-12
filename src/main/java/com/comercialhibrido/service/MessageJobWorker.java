package com.comercialhibrido.service;

import com.comercialhibrido.config.AgentProperties;
import com.comercialhibrido.domain.entity.InboundMessageJob;
import com.comercialhibrido.domain.entity.Message;
import com.comercialhibrido.domain.entity.OutboundMessageJob;
import com.comercialhibrido.domain.enums.DeliveryStatus;
import com.comercialhibrido.domain.enums.JobStatus;
import com.comercialhibrido.repository.InboundMessageJobRepository;
import com.comercialhibrido.repository.MessageRepository;
import com.comercialhibrido.repository.OutboundMessageJobRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MessageJobWorker {

    private static final Logger log = LoggerFactory.getLogger(MessageJobWorker.class);

    private final InboundMessageJobRepository inboundMessageJobRepository;
    private final OutboundMessageJobRepository outboundMessageJobRepository;
    private final MessageRepository messageRepository;
    private final BotResponseService botResponseService;
    private final WhatsAppMessageSender whatsAppMessageSender;
    private final AgentProperties agentProperties;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelayString = "${agent.worker-fixed-delay-ms:1000}")
    public void runCycle() {
        recoverStaleJobs();
        processInboundJobs();
        processOutboundJobs();
    }

    private void processInboundJobs() {
        List<InboundMessageJob> jobs = inboundMessageJobRepository
            .findTop10ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                JobStatus.PENDING, Instant.now());

        for (InboundMessageJob candidate : jobs) {
            InboundClaim claim = claimInbound(candidate.getId());
            if (claim == null) {
                continue;
            }
            try {
                botResponseService.generarYEncolarRespuesta(claim.conversationId());
                completeInbound(claim.id());
            } catch (Exception e) {
                retryInbound(claim.id(), e);
            }
        }
    }

    private void processOutboundJobs() {
        List<OutboundMessageJob> jobs = outboundMessageJobRepository
            .findTop10ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                JobStatus.PENDING, Instant.now());

        for (OutboundMessageJob candidate : jobs) {
            OutboundClaim claim = claimOutbound(candidate.getId());
            if (claim == null) {
                continue;
            }
            try {
                String externalId = whatsAppMessageSender.enviarTexto(claim.toPhoneNumber(), claim.body());
                completeOutbound(claim.id(), externalId);
            } catch (Exception e) {
                retryOutbound(claim.id(), e);
            }
        }
    }

    private InboundClaim claimInbound(UUID id) {
        return transactionTemplate.execute(status -> inboundMessageJobRepository.findById(id)
            .filter(job -> job.getStatus() == JobStatus.PENDING)
            .map(job -> {
                job.markProcessing();
                inboundMessageJobRepository.saveAndFlush(job);
                return new InboundClaim(job.getId(), job.getConversationId());
            })
            .orElse(null));
    }

    private OutboundClaim claimOutbound(UUID id) {
        return transactionTemplate.execute(status -> outboundMessageJobRepository.findById(id)
            .filter(job -> job.getStatus() == JobStatus.PENDING)
            .map(job -> {
                job.markProcessing();
                outboundMessageJobRepository.saveAndFlush(job);
                return new OutboundClaim(job.getId(), job.getToPhoneNumber(), job.getBody());
            })
            .orElse(null));
    }

    private void completeInbound(UUID id) {
        transactionTemplate.executeWithoutResult(status -> inboundMessageJobRepository.findById(id).ifPresent(job -> {
            job.markCompleted();
            inboundMessageJobRepository.save(job);
        }));
    }

    private void completeOutbound(UUID id, String externalId) {
        transactionTemplate.executeWithoutResult(status -> outboundMessageJobRepository.findById(id).ifPresent(job -> {
            job.markSent(externalId);
            outboundMessageJobRepository.save(job);
            messageRepository.findById(job.getMessageId()).ifPresent(message -> {
                message.setWhatsappMessageId(externalId);
                message.setDeliveryStatus(DeliveryStatus.SENT);
                messageRepository.save(message);
            });
        }));
    }

    private void retryInbound(UUID id, Exception error) {
        transactionTemplate.executeWithoutResult(status -> inboundMessageJobRepository.findById(id).ifPresent(job -> {
            boolean exhausted = job.getAttempts() + 1 >= agentProperties.maxAttemptsOrDefault();
            job.markRetry(nextAttempt(job.getAttempts()), error.getMessage(), exhausted);
            inboundMessageJobRepository.save(job);
            log.error("Fallo procesando job entrante {} (agotado={}): {}", id, exhausted,
                error.getMessage(), error);
        }));
    }

    private void retryOutbound(UUID id, Exception error) {
        transactionTemplate.executeWithoutResult(status -> outboundMessageJobRepository.findById(id).ifPresent(job -> {
            boolean exhausted = job.getAttempts() + 1 >= agentProperties.maxAttemptsOrDefault();
            job.markRetry(nextAttempt(job.getAttempts()), error.getMessage(), exhausted);
            outboundMessageJobRepository.save(job);
            messageRepository.findById(job.getMessageId()).ifPresent(message -> {
                if (exhausted) {
                    message.setDeliveryStatus(DeliveryStatus.FAILED);
                    message.setDeliveryError(error.getMessage());
                    messageRepository.save(message);
                }
            });
            log.error("Fallo enviando job saliente {} (agotado={}): {}", id, exhausted,
                error.getMessage(), error);
        }));
    }

    private Instant nextAttempt(int attemptsAlreadyMade) {
        long multiplier = 1L << Math.min(attemptsAlreadyMade, 8);
        long seconds = Math.min(
            3600L,
            agentProperties.retryBaseSecondsOrDefault() * multiplier
        );
        return Instant.now().plusSeconds(seconds);
    }

    private void recoverStaleJobs() {
        Instant before = Instant.now().minus(Duration.ofMinutes(agentProperties.staleJobMinutesOrDefault()));
        transactionTemplate.executeWithoutResult(status -> {
            inboundMessageJobRepository
                .findTop10ByStatusAndLockedAtBeforeOrderByCreatedAtAsc(JobStatus.PROCESSING, before)
                .forEach(job -> {
                    job.setStatus(JobStatus.PENDING);
                    job.setLockedAt(null);
                    job.setNextAttemptAt(Instant.now());
                    inboundMessageJobRepository.save(job);
                });
            outboundMessageJobRepository
                .findTop10ByStatusAndLockedAtBeforeOrderByCreatedAtAsc(JobStatus.PROCESSING, before)
                .forEach(job -> {
                    job.setStatus(JobStatus.PENDING);
                    job.setLockedAt(null);
                    job.setNextAttemptAt(Instant.now());
                    outboundMessageJobRepository.save(job);
                });
        });
    }

    private record InboundClaim(UUID id, UUID conversationId) {}
    private record OutboundClaim(UUID id, String toPhoneNumber, String body) {}
}
