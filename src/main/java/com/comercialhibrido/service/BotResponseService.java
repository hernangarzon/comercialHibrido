package com.comercialhibrido.service;

import com.comercialhibrido.domain.entity.Conversation;
import com.comercialhibrido.domain.entity.Message;
import com.comercialhibrido.domain.entity.OutboundMessageJob;
import com.comercialhibrido.domain.enums.DeliveryStatus;
import com.comercialhibrido.domain.enums.JobStatus;
import com.comercialhibrido.domain.enums.MessageSender;
import com.comercialhibrido.dto.openai.OpenAiChatDtos.BotAnswer;
import com.comercialhibrido.dto.openai.OpenAiChatDtos.ChatMessage;
import com.comercialhibrido.repository.MessageRepository;
import com.comercialhibrido.repository.OutboundMessageJobRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.comercialhibrido.domain.event.ConversationActivityEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BotResponseService {

    private static final Logger log = LoggerFactory.getLogger(BotResponseService.class);
    private static final int UMBRAL_ESCALAMIENTO_POR_SCORE = 70;
    private static final String MENSAJE_FALLBACK =
        "Gracias por tu mensaje. Voy a transferir tu consulta a un asesor comercial para darte una atención personalizada.";

    private final ConversationRepositoryReader conversationRepositoryReader;
    private final ConversationContextBuilder contextBuilder;
    private final OpenAiChatClient openAiChatClient;
    private final MessageRepository messageRepository;
    private final OutboundMessageJobRepository outboundMessageJobRepository;
    private final ConversationStateService conversationStateService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void generarYEncolarRespuesta(UUID conversationId) {
        Conversation conversation = conversationRepositoryReader.buscarConCliente(conversationId);

        if (!conversationStateService.debeResponderBot(conversation)) {
            log.debug("Conversación {} en control humano, el bot no responde.", conversationId);
            return;
        }

        String telefonoCliente = conversation.getCustomer().getPhoneNumber();

        try {
            List<ChatMessage> contexto = contextBuilder.construir(conversation);
            BotAnswer answer = openAiChatClient.generarRespuesta(contexto);

            persistirYEncolarSalida(conversation, answer.respuesta(), telefonoCliente);

            boolean debeEscalar = Boolean.TRUE.equals(answer.requiereEscalamiento())
                || (answer.leadScore() != null && answer.leadScore() >= UMBRAL_ESCALAMIENTO_POR_SCORE);

            if (debeEscalar) {
                conversationStateService.escalar(conversationId, answer.leadScore());
            } else if (answer.leadScore() != null) {
                // Antes el score solo se guardaba al escalar; el panel mostraba 0 en el resto.
                conversation.setLeadScore(answer.leadScore());
            }

        } catch (Exception e) {
            log.error("Fallo generando respuesta de IA para conversación {}: {}",
                conversationId, e.getMessage(), e);

            // Ante cualquier fallo del proveedor de IA, se envía mensaje de cortesía y se escala a humano
            persistirYEncolarSalida(conversation, MENSAJE_FALLBACK, telefonoCliente);
            conversationStateService.escalar(conversationId, conversation.getLeadScore());
        }
    }

    private void persistirYEncolarSalida(Conversation conversation, String texto, String telefonoCliente) {
        Message mensaje = messageRepository.save(
            Message.builder()
                .conversation(conversation)
                .sender(MessageSender.BOT)
                .content(texto)
                .deliveryStatus(DeliveryStatus.PENDING)
                .build()
        );

        outboundMessageJobRepository.save(
            OutboundMessageJob.builder()
                .conversationId(conversation.getId())
                .messageId(mensaje.getId())
                .toPhoneNumber(telefonoCliente)
                .body(texto)
                .status(JobStatus.PENDING)
                .deliveryStatus(DeliveryStatus.PENDING)
                .nextAttemptAt(Instant.now())
                .build()
        );
        eventPublisher.publishEvent(
            new ConversationActivityEvent(conversation.getId(), ConversationActivityEvent.Type.BOT_REPLY));
    }
}