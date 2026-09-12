package com.comercialhibrido.service;

import com.comercialhibrido.domain.entity.*;
import com.comercialhibrido.domain.enums.ConversationStatus;
import com.comercialhibrido.domain.enums.JobStatus;
import com.comercialhibrido.domain.enums.MessageSender;
import com.comercialhibrido.dto.whatsapp.WhatsAppWebhookPayload;
import com.comercialhibrido.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IncomingMessageService {

    private static final Logger log = LoggerFactory.getLogger(IncomingMessageService.class);

    private final CompanyRepository companyRepository;
    private final CustomerRepository customerRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final InboundMessageJobRepository inboundMessageJobRepository;

    @Transactional
    public Optional<Conversation> processIncomingTextMessage(
        String targetPhoneNumberId,
        String fromPhoneNumber,
        String profileName,
        WhatsAppWebhookPayload.IncomingMessage incomingMessage
    ) {
        if (messageRepository.existsByWhatsappMessageId(incomingMessage.id())) {
            log.info("Mensaje {} ya fue procesado previamente, se ignora.", incomingMessage.id());
            return Optional.empty();
        }

        Company company = companyRepository.findByWhatsappPhoneNumberIdAndActiveTrue(targetPhoneNumberId)
            .orElse(null);

        if (company == null) {
            log.warn("Mensaje recibido para Phone Number ID no registrado: {}", targetPhoneNumberId);
            return Optional.empty();
        }

        Customer customer = customerRepository.findByPhoneNumber(fromPhoneNumber)
            .orElseGet(() -> customerRepository.save(
                Customer.builder()
                    .phoneNumber(fromPhoneNumber)
                    .displayName(profileName)
                    .build()
            ));

        if ((customer.getDisplayName() == null || customer.getDisplayName().isBlank()) && profileName != null) {
            customer.setDisplayName(profileName);
            customerRepository.save(customer);
        }

        Conversation conversation = conversationRepository
            .findFirstByCustomerIdOrderByCreatedAtDesc(customer.getId())
            .orElseGet(() -> conversationRepository.save(
                Conversation.builder()
                    .company(company)
                    .customer(customer)
                    .status(ConversationStatus.BOT_ACTIVO)
                    .build()
            ));

        if (conversation.getStatus() == ConversationStatus.ARCHIVADO) {
            conversation.setStatus(ConversationStatus.BOT_ACTIVO);
            conversationRepository.save(conversation);
        }

        // Extracción de contenido, tipo y archivo multimedia
        String body;
        String mediaType = "TEXT";
        String mediaId = null;
        String mediaFilename = null;

        if ("image".equalsIgnoreCase(incomingMessage.type()) && incomingMessage.image() != null) {
            mediaType = "IMAGE";
            mediaId = incomingMessage.image().id();
            String caption = incomingMessage.image().caption();
            body = (caption != null && !caption.isBlank()) ? caption : "[Comprobante / Imagen recibida]";
        } else if ("document".equalsIgnoreCase(incomingMessage.type()) && incomingMessage.document() != null) {
            mediaType = "DOCUMENT";
            mediaId = incomingMessage.document().id();
            mediaFilename = incomingMessage.document().filename();
            String caption = incomingMessage.document().caption();
            body = (caption != null && !caption.isBlank()) ? caption : "[Documento PDF recibido: " + (mediaFilename != null ? mediaFilename : "Archivo") + "]";
        } else if (incomingMessage.text() != null && incomingMessage.text().body() != null) {
            body = incomingMessage.text().body();
        } else {
            body = "[Mensaje de tipo: " + incomingMessage.type() + "]";
        }

        messageRepository.save(
            Message.builder()
                .conversation(conversation)
                .sender(MessageSender.CLIENTE)
                .content(body)
                .whatsappMessageId(incomingMessage.id())
                .mediaType(mediaType)
                .mediaId(mediaId)
                .mediaFilename(mediaFilename)
                .build()
        );

        inboundMessageJobRepository.save(
            InboundMessageJob.builder()
                .whatsappMessageId(incomingMessage.id())
                .conversationId(conversation.getId())
                .status(JobStatus.PENDING)
                .nextAttemptAt(Instant.now())
                .build()
        );

        return Optional.of(conversation);
    }
}