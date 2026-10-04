package com.comercialhibrido.service;

import com.comercialhibrido.domain.event.ConversationActivityEvent;
import com.comercialhibrido.repository.ConversationRepository;
import com.comercialhibrido.security.WebSocketAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Reenvía la actividad de conversaciones al panel de la empresa por WebSocket.
 * AFTER_COMMIT: el panel solo se entera de cambios que realmente se guardaron.
 */
@Component
@RequiredArgsConstructor
public class ConversationActivityListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ConversationRepository conversationRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onActivity(ConversationActivityEvent event) {
        conversationRepository.findCompanyIdById(event.conversationId()).ifPresent(companyId ->
            messagingTemplate.convertAndSend(
                WebSocketAuthInterceptor.COMPANY_TOPIC_PREFIX + companyId,
                Map.of("type", event.type().name(), "conversationId", event.conversationId().toString())
            )
        );
    }
}
