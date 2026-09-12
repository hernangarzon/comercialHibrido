package com.comercialhibrido.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // El panel se suscribe a destinos bajo /topic (ej: /topic/conversaciones)
        // para recibir notificaciones broadcast, como una escalacion.
        registry.enableSimpleBroker("/topic");

        // Prefijo reservado para mensajes que el panel envie hacia el
        // backend por WebSocket (no lo usamos todavia, pero es obligatorio
        // declararlo junto con enableSimpleBroker).
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Punto de conexion inicial que el panel Angular usa para abrir
        // el WebSocket. withSockJS() agrega un fallback automatico a
        // polling HTTP si el navegador o la red bloquean WebSocket nativo.
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*") // ajustar a tu dominio real antes de produccion
            .withSockJS();
    }
}