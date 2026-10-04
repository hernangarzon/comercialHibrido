package com.comercialhibrido.service;

import com.comercialhibrido.domain.entity.Message;
import com.comercialhibrido.domain.enums.DeliveryStatus;
import com.comercialhibrido.dto.whatsapp.WhatsAppWebhookPayload;
import com.comercialhibrido.repository.MessageRepository;
import com.comercialhibrido.repository.OutboundMessageJobRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.comercialhibrido.domain.event.ConversationActivityEvent;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class WhatsAppDeliveryStatusService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppDeliveryStatusService.class);

    private final OutboundMessageJobRepository outboundMessageJobRepository;
    private final MessageRepository messageRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void processStatus(WhatsAppWebhookPayload.StatusUpdate update) {
        if (update == null || update.id() == null || update.status() == null) {
            return;
        }

        DeliveryStatus deliveryStatus = mapStatus(update.status());
        outboundMessageJobRepository.findByWhatsappMessageId(update.id()).ifPresent(job -> {
            job.setDeliveryStatus(deliveryStatus);
            if (deliveryStatus == DeliveryStatus.FAILED) {
                job.setLastError(update.errorSummary());
            }
            outboundMessageJobRepository.save(job);

            messageRepository.findById(job.getMessageId()).ifPresent(message ->
                updateMessage(message, deliveryStatus, update.errorSummary())
            );
            eventPublisher.publishEvent(
                new ConversationActivityEvent(job.getConversationId(), ConversationActivityEvent.Type.DELIVERY));
        });
    }

    private void updateMessage(Message message, DeliveryStatus status, String error) {
        message.setDeliveryStatus(status);
        if (status == DeliveryStatus.FAILED) {
            message.setDeliveryError(error);
        }
        messageRepository.save(message);
    }

    private DeliveryStatus mapStatus(String rawStatus) {
        return switch (rawStatus.toLowerCase(Locale.ROOT)) {
            case "sent" -> DeliveryStatus.SENT;
            case "delivered" -> DeliveryStatus.DELIVERED;
            case "read" -> DeliveryStatus.READ;
            case "failed" -> DeliveryStatus.FAILED;
            default -> {
                log.debug("Estado de WhatsApp no reconocido: {}", rawStatus);
                yield DeliveryStatus.PENDING;
            }
        };
    }
}
