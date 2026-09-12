package com.comercialhibrido.service;

import com.comercialhibrido.domain.entity.Conversation;
import com.comercialhibrido.domain.entity.Message;
import com.comercialhibrido.domain.entity.OutboundMessageJob;
import com.comercialhibrido.domain.enums.ConversationStatus;
import com.comercialhibrido.domain.enums.DeliveryStatus;
import com.comercialhibrido.domain.enums.JobStatus;
import com.comercialhibrido.domain.enums.MessageSender;
import com.comercialhibrido.exception.IllegalTransitionException;
import com.comercialhibrido.repository.MessageRepository;
import com.comercialhibrido.repository.OutboundMessageJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutgoingMessageService {

    private final ConversationRepositoryReader conversationRepositoryReader;
    private final MessageRepository messageRepository;
    private final OutboundMessageJobRepository outboundMessageJobRepository;

    @Transactional
    public void enviarMensajeComercial(UUID conversationId, String content) {
        Conversation conversation = conversationRepositoryReader.buscarConCliente(conversationId);

        if (conversation.getStatus() != ConversationStatus.HUMANO_CONTROL) {
            throw new IllegalTransitionException(
                "No se puede enviar mensaje manual: la conversación " + conversationId +
                " no está en HUMANO_CONTROL (estado actual: " + conversation.getStatus() + "). " +
                "Toma control de la conversación primero."
            );
        }

        Message mensaje = messageRepository.save(
            Message.builder()
                .conversation(conversation)
                .sender(MessageSender.COMERCIAL)
                .content(content)
                .deliveryStatus(DeliveryStatus.PENDING)
                .build()
        );

        // Se encola en el Outbox para que el worker lo entregue a Meta
        outboundMessageJobRepository.save(
            OutboundMessageJob.builder()
                .conversationId(conversation.getId())
                .messageId(mensaje.getId())
                .toPhoneNumber(conversation.getCustomer().getPhoneNumber())
                .body(content)
                .status(JobStatus.PENDING)
                .deliveryStatus(DeliveryStatus.PENDING)
                .nextAttemptAt(Instant.now())
                .build()
        );
    }
}