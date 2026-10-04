package com.comercialhibrido.repository;

import com.comercialhibrido.domain.enums.MessageSender;

import java.time.Instant;
import java.util.UUID;

/** Proyección liviana de un mensaje para calcular métricas. */
public record MessageStat(UUID conversationId, MessageSender sender, Instant createdAt) {}
