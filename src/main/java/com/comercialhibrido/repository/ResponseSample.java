package com.comercialhibrido.repository;

import java.time.Instant;
import java.util.UUID;

/** Escalación y primera respuesta de un asesor posterior a ella. */
public record ResponseSample(UUID conversationId, Instant escalatedAt, Instant firstReplyAt) {}
