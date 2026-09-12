package com.comercialhibrido.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mapea el bloque "whatsapp" de application.yml. Usar propiedades tipadas
 * en vez de @Value disperso permite validar configuracion faltante en un
 * solo lugar y facilita testear el resto del codigo con valores mock.
 */
@ConfigurationProperties(prefix = "whatsapp")
public record WhatsAppProperties(
    String apiBaseUrl,
    String phoneNumberId,
    String accessToken,
    String webhookVerifyToken
) {}