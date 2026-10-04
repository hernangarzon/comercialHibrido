package com.comercialhibrido.service;

import java.time.Duration;
import java.time.Instant;

/**
 * Ventana de atención de WhatsApp: tras el último mensaje del cliente hay 24 horas
 * para responder con texto libre. Pasado ese plazo Meta solo acepta plantillas
 * aprobadas (si no, rechaza el envío con el error 131047).
 */
public final class CustomerServiceWindow {

    public static final Duration LENGTH = Duration.ofHours(24);

    private CustomerServiceWindow() {}

    /** Momento en que cierra la ventana, o null si el cliente nunca escribió. */
    public static Instant closesAt(Instant lastClientMessageAt) {
        return lastClientMessageAt == null ? null : lastClientMessageAt.plus(LENGTH);
    }

    public static boolean isOpen(Instant lastClientMessageAt, Instant now) {
        Instant closes = closesAt(lastClientMessageAt);
        return closes != null && now.isBefore(closes);
    }
}
