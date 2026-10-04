package com.comercialhibrido.security;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebSocketAuthInterceptorTest {

    private final JwtService jwtService = new JwtService("secreto-de-pruebas-con-mas-de-32-caracteres");
    private final WebSocketAuthInterceptor interceptor = new WebSocketAuthInterceptor(jwtService);
    private final UUID companyId = UUID.randomUUID();

    @Test
    void connectSinTokenORechazado() {
        assertThatThrownBy(() -> interceptor.preSend(connect(null), null))
            .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(connect("Bearer basura"), null))
            .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void connectConTokenValidoAsignaElUsuario() {
        Message<?> resultado = interceptor.preSend(connect("Bearer " + token()), null);
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(resultado);
        assertThat(accessor.getUser()).isInstanceOf(WebSocketAuthInterceptor.PanelPrincipal.class);
    }

    @Test
    void soloPermiteSuscribirseAlTopicDeSuEmpresa() {
        var principal = new WebSocketAuthInterceptor.PanelPrincipal(jwtService.validarYExtraer(token()));

        assertThat(interceptor.preSend(subscribe("/topic/empresa/" + companyId, principal), null)).isNotNull();
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/empresa/" + UUID.randomUUID(), principal), null))
            .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/conversaciones", principal), null))
            .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/empresa/" + companyId, null), null))
            .isInstanceOf(MessageDeliveryException.class);
    }

    private String token() {
        return jwtService.generarToken(UUID.randomUUID(), companyId, "a@b.com", "Ana", "ADMIN");
    }

    private static Message<byte[]> connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Message<byte[]> subscribe(String destination, java.security.Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(user);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
