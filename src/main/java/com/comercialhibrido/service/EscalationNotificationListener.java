package com.comercialhibrido.service;

import com.comercialhibrido.domain.entity.Conversation;
import com.comercialhibrido.domain.event.ConversationEscalatedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class EscalationNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(EscalationNotificationListener.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ConversationRepositoryReader conversationRepositoryReader;

    /**
     * Escucha ConversationEscalatedEvent (publicado por ConversationStateService.escalar())
     * y lo reenvia al panel por WebSocket.
     *
     * @TransactionalEventListener con AFTER_COMMIT en vez de @EventListener:
     * esto asegura que la notificacion solo se envie si la transaccion que
     * cambio el estado a ESCALADO_PENDIENTE realmente se confirmo en base
     * de datos. Si usaramos @EventListener normal, el evento se procesaria
     * en el mismo instante de publishEvent(), que ocurre ANTES del commit;
     * si algo fallara despues y la transaccion hiciera rollback, el panel
     * ya habria recibido una notificacion de un cambio que nunca se guardo.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConversationEscalated(ConversationEscalatedEvent event) {
        Conversation conversation = conversationRepositoryReader.buscarConCliente(event.conversationId());

        EscalationNotification notification = new EscalationNotification(
            conversation.getId().toString(),
            conversation.getCustomer().getPhoneNumber(),
            conversation.getCustomer().getDisplayName(),
            conversation.getSummary(),
            event.leadScore()
        );

        messagingTemplate.convertAndSend("/topic/conversaciones", notification);
        log.info("Notificacion de escalacion enviada al panel para conversacion {}.", event.conversationId());
    }

    private record EscalationNotification(
        String conversationId,
        String customerPhone,
        String customerName,
        String summary,
        Integer leadScore
    ) {}
}