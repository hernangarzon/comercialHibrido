package com.comercialhibrido.domain.event;

import java.util.UUID;

/**
 * Algo cambió en una conversación y el panel debe refrescarla en vivo
 * (mensaje nuevo, respuesta del bot, cambio de estado o de entrega).
 */
public record ConversationActivityEvent(
    UUID conversationId,
    Type type
) {
    public enum Type {
        NEW_MESSAGE,
        BOT_REPLY,
        STATUS_CHANGED,
        DELIVERY
    }
}
