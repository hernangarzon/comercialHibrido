package com.comercialhibrido.service;

import com.comercialhibrido.domain.entity.Conversation;
import com.comercialhibrido.domain.enums.ConversationStatus;
import com.comercialhibrido.domain.event.ConversationEscalatedEvent;
import com.comercialhibrido.exception.IllegalTransitionException;
import com.comercialhibrido.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationStateService {

    private static final Logger log = LoggerFactory.getLogger(ConversationStateService.class);

    private final ConversationRepository conversationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public boolean debeResponderBot(Conversation conversation) {
        // El bot SOLO responde si está en BOT_ACTIVO.
        // Si está en ESCALADO_PENDIENTE, HUMANO_CONTROL o ARCHIVADO, guarda silencio.
        return conversation.getStatus() == ConversationStatus.BOT_ACTIVO;
    }

    @Transactional
    public Conversation tomarControl(UUID conversationId, UUID salespersonId) {
        Conversation conversation = obtenerOFallar(conversationId);
        conversation.setStatus(ConversationStatus.HUMANO_CONTROL);
        conversation.setAssignedSalespersonId(salespersonId);
        return guardarConManejoDeConflicto(conversation);
    }

    @Transactional
    public Conversation liberarControl(UUID conversationId) {
        Conversation conversation = obtenerOFallar(conversationId);
        conversation.setStatus(ConversationStatus.BOT_ACTIVO);
        conversation.setAssignedSalespersonId(null);
        return guardarConManejoDeConflicto(conversation);
    }

    @Transactional
    public Conversation archivar(UUID conversationId) {
        Conversation conversation = obtenerOFallar(conversationId);
        conversation.setStatus(ConversationStatus.ARCHIVADO);
        conversation.setAssignedSalespersonId(null);
        log.info("Conversación {} archivada.", conversationId);
        return guardarConManejoDeConflicto(conversation);
    }

    @Transactional
    public Conversation escalar(UUID conversationId, int leadScore) {
        Conversation conversation = obtenerOFallar(conversationId);
        conversation.setLeadScore(leadScore);

        if (conversation.getStatus() == ConversationStatus.HUMANO_CONTROL) {
            log.debug("Conversacion {} ya esta en HUMANO_CONTROL, se actualiza leadScore sin re-escalar.",
                conversationId);
            return guardarConManejoDeConflicto(conversation);
        }

        conversation.setStatus(ConversationStatus.ESCALADO_PENDIENTE);
        Conversation actualizada = guardarConManejoDeConflicto(conversation);

        eventPublisher.publishEvent(new ConversationEscalatedEvent(conversationId, leadScore));
        log.info("Conversacion {} escalada con leadScore={}.", conversationId, leadScore);

        return actualizada;
    }

    private Conversation obtenerOFallar(UUID conversationId) {
        return conversationRepository.findById(conversationId)
            .orElseThrow(() -> new IllegalTransitionException(
                "Conversacion no encontrada: " + conversationId));
    }

    private Conversation guardarConManejoDeConflicto(Conversation conversation) {
        try {
            return conversationRepository.saveAndFlush(conversation);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new IllegalTransitionException(
                "La conversacion " + conversation.getId() +
                " fue modificada concurrentemente por otro proceso. Reintenta la accion."
            );
        }
    }
}