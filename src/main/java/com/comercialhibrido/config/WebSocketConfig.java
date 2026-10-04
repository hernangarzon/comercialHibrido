package com.comercialhibrido.config;

import com.comercialhibrido.security.WebSocketAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AgentProperties agentProperties;
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // El panel se suscribe a /topic/empresa/{companyId} para recibir
        // notificaciones de su propia empresa, como una escalación.
        registry.enableSimpleBroker("/topic");

        // Prefijo reservado para mensajes que el panel envie hacia el
        // backend por WebSocket (no lo usamos todavia, pero es obligatorio
        // declararlo junto con enableSimpleBroker).
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // El panel servido por esta misma app (mismo origen) siempre se acepta;
        // PANEL_ALLOWED_ORIGINS solo hace falta si el panel vive en otro dominio.
        // withSockJS() agrega un fallback automatico a polling HTTP si el
        // navegador o la red bloquean WebSocket nativo.
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns(allowedOrigins())
            .withSockJS();
    }

    private String[] allowedOrigins() {
        String raw = agentProperties.panelAllowedOrigins();
        if (raw == null || raw.isBlank()) {
            return new String[0];
        }
        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toArray(String[]::new);
    }
}
