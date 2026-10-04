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
import com.comercialhibrido.domain.event.ConversationActivityEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutgoingMessageService {

    /** Tipo de mensaje en el historial para distinguir las plantillas del texto libre. */
    public static final String TEMPLATE_MEDIA_TYPE = "TEMPLATE";

    private final ConversationRepositoryReader conversationRepositoryReader;
    private final MessageRepository messageRepository;
    private final OutboundMessageJobRepository outboundMessageJobRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final WhatsAppTemplateService templateService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void enviarMensajeComercial(UUID conversationId, String content) {
        Conversation conversation = enControlHumano(conversationId);

        // Fuera de la ventana de 24 h Meta rechaza el texto libre: se avisa antes de encolarlo.
        Instant lastClient = messageRepository.lastClientMessageAtInConversation(conversationId);
        if (!CustomerServiceWindow.isOpen(lastClient, Instant.now())) {
            throw new IllegalTransitionException(
                "Pasaron más de 24 horas desde el último mensaje del cliente. WhatsApp solo permite "
                    + "escribirle con una plantilla aprobada; cuando responda, podrás escribir libremente.");
        }

        encolar(conversation, content, OutboundMessageJob.builder().body(content), "TEXT");
    }

    /**
     * Envía una plantilla aprobada en Meta. Funciona dentro o fuera de la ventana de 24 h
     * y es la forma de retomar el contacto con un cliente que dejó de escribir.
     */
    @Transactional
    public void enviarPlantilla(UUID conversationId, String name, String language, List<String> params) {
        Conversation conversation = enControlHumano(conversationId);
        WhatsAppTemplateService.Template template = templateService.buscar(conversation.getCompany(), name, language);

        List<String> values = params == null ? List.of() : params.stream().map(String::strip).toList();
        if (values.size() != template.paramCount() || values.stream().anyMatch(String::isEmpty)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La plantilla «" + name + "» necesita " + template.paramCount() + " dato(s) y todos deben estar completos.");
        }

        String rendered = template.render(values);
        encolar(conversation, rendered, OutboundMessageJob.builder()
            .body(rendered)
            .templateName(template.name())
            .templateLanguage(template.language())
            .templateParams(toJson(values)), TEMPLATE_MEDIA_TYPE);
    }

    private Conversation enControlHumano(UUID conversationId) {
        Conversation conversation = conversationRepositoryReader.buscarConCliente(conversationId);
        if (conversation.getStatus() != ConversationStatus.HUMANO_CONTROL) {
            throw new IllegalTransitionException(
                "No se puede enviar mensaje manual: la conversación " + conversationId +
                " no está en HUMANO_CONTROL (estado actual: " + conversation.getStatus() + "). " +
                "Toma control de la conversación primero."
            );
        }
        return conversation;
    }

    private void encolar(Conversation conversation, String content, OutboundMessageJob.OutboundMessageJobBuilder job, String mediaType) {
        Message mensaje = messageRepository.save(
            Message.builder()
                .conversation(conversation)
                .sender(MessageSender.COMERCIAL)
                .content(content)
                .mediaType(mediaType)
                .deliveryStatus(DeliveryStatus.PENDING)
                .build()
        );

        // Se encola en el Outbox para que el worker lo entregue a Meta
        outboundMessageJobRepository.save(
            job.conversationId(conversation.getId())
                .messageId(mensaje.getId())
                .toPhoneNumber(conversation.getCustomer().getPhoneNumber())
                .status(JobStatus.PENDING)
                .deliveryStatus(DeliveryStatus.PENDING)
                .nextAttemptAt(Instant.now())
                .build()
        );
        eventPublisher.publishEvent(
            new ConversationActivityEvent(conversation.getId(), ConversationActivityEvent.Type.NEW_MESSAGE));
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
