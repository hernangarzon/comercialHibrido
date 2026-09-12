package com.comercialhibrido.domain.event;

import java.util.UUID;

public record ConversationEscalatedEvent(
    UUID conversationId,
    Integer leadScore
) {}