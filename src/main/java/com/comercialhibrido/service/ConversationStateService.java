package com.comercialhibrido.service;

import com.comercialhibrido.domain.entity.Conversation;
import com.comercialhibrido.domain.enums.ConversationStatus;
import com.comercialhibrido.domain.event.ConversationActivityEvent;
import com.comercialhibrido.domain.event.ConversationEscalatedEvent;
import com.comercialhibrido.exception.IllegalTransitionException;
import com.comercialhibrido.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationStateService {

    private static final Logger log = LoggerFactory.getLogger(ConversationStateService.class);

    private final ConversationRepository conversationRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** Minutos sin que el asesor escriba tras tomar el control para que el bot la retome. */
    @Value("${agent.human-idle-minutes:30}")
    private long humanIdleMinutes = 30;

    /**
     * El bot atiende siempre, también mientras una conversación espera a un asesor
     * (ESCALADO_PENDIENTE): el cliente nunca debe quedar sin respuesta. Solo calla
     * cuando un asesor tomó el control y está activo.
     */
    public boolean debeResponderBot(Conversation conversation) {
        return conversation.getStatus() == ConversationStatus.BOT_ACTIVO
            || conversation.getStatus() == ConversationStatus.ESCALADO_PENDIENTE;
    }

    /**
     * Un asesor tomó el control pero no escribió en {@code humanIdleMinutes} (contados desde
     * que lo tomó o desde su último mensaje): el cliente no puede quedar esperando.
     */
    public boolean asesorInactivo(Conversation conversation, Instant lastAgentMessageAt, Instant now) {
        if (conversation.getStatus() != ConversationStatus.HUMANO_CONTROL) {
            return false;
        }
        Instant ultimaActividad = latest(conversation.getTakenAt(), lastAgentMessageAt);
        // Sin registro de cuándo lo tomó (datos anteriores a V5) se considera inactivo.
        return ultimaActividad == null || ultimaActividad.isBefore(now.minus(Duration.ofMinutes(humanIdleMinutes)));
    }

    /**
     * Devuelve al bot una conversación abandonada por el asesor. Queda como escalada
     * (sigue visible para el equipo) sin volver a disparar la alerta.
     */
    @Transactional
    public Conversation retomarPorInactividad(UUID conversationId) {
        Conversation conversation = obtenerOFallar(conversationId);
        conversation.setStatus(ConversationStatus.ESCALADO_PENDIENTE);
        conversation.setAssignedSalespersonId(null);
        conversation.setTakenAt(null);
        log.info("Conversación {} vuelve al bot: el asesor no respondió en {} min.", conversationId, humanIdleMinutes);
        return guardarConManejoDeConflicto(conversation);
    }

    @Transactional
    public Conversation tomarControl(UUID conversationId, UUID salespersonId) {
        Conversation conversation = obtenerOFallar(conversationId);
        conversation.setStatus(ConversationStatus.HUMANO_CONTROL);
        conversation.setAssignedSalespersonId(salespersonId);
        conversation.setTakenAt(Instant.now());
        return guardarConManejoDeConflicto(conversation);
    }

    @Transactional
    public Conversation liberarControl(UUID conversationId) {
        Conversation conversation = obtenerOFallar(conversationId);
        conversation.setStatus(ConversationStatus.BOT_ACTIVO);
        conversation.setAssignedSalespersonId(null);
        conversation.setTakenAt(null);
        return guardarConManejoDeConflicto(conversation);
    }

    @Transactional
    public Conversation archivar(UUID conversationId) {
        Conversation conversation = obtenerOFallar(conversationId);
        conversation.setStatus(ConversationStatus.ARCHIVADO);
        conversation.setAssignedSalespersonId(null);
        conversation.setTakenAt(null);
        log.info("Conversación {} archivada.", conversationId);
        return guardarConManejoDeConflicto(conversation);
    }

    /**
     * Avisa al equipo que la conversación necesita un asesor. Si ya estaba escalada o en
     * manos de un asesor, solo actualiza score y resumen: la alerta no se repite con cada
     * mensaje del cliente y se conserva la hora de la primera escalación (métricas).
     */
    @Transactional
    public Conversation escalar(UUID conversationId, int leadScore, String resumen) {
        Conversation conversation = obtenerOFallar(conversationId);
        conversation.setLeadScore(leadScore);
        if (resumen != null && !resumen.isBlank()) {
            conversation.setSummary(resumen.strip());
            conversation.setLastSummarizedAt(Instant.now());
        }

        if (conversation.getStatus() == ConversationStatus.HUMANO_CONTROL
            || conversation.getStatus() == ConversationStatus.ESCALADO_PENDIENTE) {
            log.debug("Conversación {} ya está con el equipo; se actualiza score/resumen sin nueva alerta.", conversationId);
            return guardarConManejoDeConflicto(conversation);
        }

        conversation.setStatus(ConversationStatus.ESCALADO_PENDIENTE);
        conversation.setEscalatedAt(Instant.now());
        Conversation actualizada = guardarConManejoDeConflicto(conversation);

        eventPublisher.publishEvent(new ConversationEscalatedEvent(conversationId, leadScore));
        log.info("Conversación {} escalada con leadScore={}.", conversationId, leadScore);

        return actualizada;
    }

    private static Instant latest(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private Conversation obtenerOFallar(UUID conversationId) {
        return conversationRepository.findById(conversationId)
            .orElseThrow(() -> new IllegalTransitionException(
                "Conversacion no encontrada: " + conversationId));
    }

    private Conversation guardarConManejoDeConflicto(Conversation conversation) {
        try {
            Conversation guardada = conversationRepository.saveAndFlush(conversation);
            eventPublisher.publishEvent(
                new ConversationActivityEvent(guardada.getId(), ConversationActivityEvent.Type.STATUS_CHANGED));
            return guardada;
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new IllegalTransitionException(
                "La conversacion " + conversation.getId() +
                " fue modificada concurrentemente por otro proceso. Reintenta la accion."
            );
        }
    }
}
