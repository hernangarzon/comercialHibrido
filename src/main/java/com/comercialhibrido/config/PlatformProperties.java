package com.comercialhibrido.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.Locale;

/**
 * Configuración del dueño de la plataforma (no de cada empresa cliente).
 *
 * @param adminEmails   correos separados por coma que ven las solicitudes de la landing
 * @param salesWhatsapp número de ventas para el botón de WhatsApp de la landing (opcional)
 */
@ConfigurationProperties(prefix = "platform")
public record PlatformProperties(String adminEmails, String salesWhatsapp) {

    public boolean isPlatformAdmin(String email) {
        if (adminEmails == null || adminEmails.isBlank() || email == null) return false;
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(adminEmails.split(","))
            .map(e -> e.trim().toLowerCase(Locale.ROOT))
            .anyMatch(normalized::equals);
    }

    public String salesWhatsappDigits() {
        return salesWhatsapp == null ? "" : salesWhatsapp.replaceAll("[^0-9]", "");
    }
}
