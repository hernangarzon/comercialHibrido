package com.comercialhibrido.repository;

import java.time.Instant;
import java.util.UUID;

/** Un instante asociado a una conversación (p. ej. el último mensaje del cliente). */
public record ConversationInstant(UUID conversationId, Instant at) {}
