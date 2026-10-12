package com.comercialhibrido.service;

import com.comercialhibrido.domain.entity.Conversation;
import com.comercialhibrido.domain.entity.Message;
import com.comercialhibrido.domain.enums.ConversationStatus;
import com.comercialhibrido.domain.enums.MessageSender;
import com.comercialhibrido.repository.ConversationRepository;
import com.comercialhibrido.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Cada minuto busca conversaciones que un asesor tomó pero dejó sin responder: si el
 * último mensaje es del cliente y el asesor está inactivo, el bot la retoma y contesta.
 * Así un cliente nunca queda esperando porque el asesor no está o no leyó.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.worker-enabled", havingValue = "true", matchIfMissing = true)
public class AbandonedConversationSweeper {

    private static final Logger log = LoggerFactory.getLogger(AbandonedConversationSweeper.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ConversationStateService conversationStateService;
    private final BotResponseService botResponseService;

    @Scheduled(fixedDelayString = "${agent.abandoned-sweep-ms:60000}", initialDelayString = "${agent.abandoned-sweep-ms:60000}")
    public void revisar() {
        Instant now = Instant.now();
        for (Conversation c : conversationRepository.findByStatus(ConversationStatus.HUMANO_CONTROL)) {
            try {
                Message last = messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(c.getId()).orElse(null);
                if (last == null || last.getSender() != MessageSender.CLIENTE) {
                    continue; // nada pendiente de responder
                }
                Instant lastAgent = messageRepository.lastAgentMessageAtInConversation(c.getId());
                if (conversationStateService.asesorInactivo(c, lastAgent, now)) {
                    botResponseService.generarYEncolarRespuesta(c.getId());
                }
            } catch (Exception e) {
                log.error("No se pudo retomar la conversación abandonada {}: {}", c.getId(), e.getMessage(), e);
            }
        }
    }
}
