package com.comercialhibrido.security;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Autentica el WebSocket del panel y aísla las notificaciones por empresa.
 *
 * El navegador no puede enviar headers HTTP propios en el handshake de SockJS,
 * así que el JWT viaja en el frame STOMP CONNECT ("Authorization: Bearer ...").
 * Luego cada SUBSCRIBE solo se permite al topic de la empresa del token.
 */
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    public static final String COMPANY_TOPIC_PREFIX = "/topic/empresa/";

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader("Authorization");
            String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
            JwtService.JwtPayload payload = jwtService.validarYExtraer(token);
            if (payload == null) {
                throw new MessageDeliveryException("Token inválido o ausente en CONNECT");
            }
            accessor.setUser(new PanelPrincipal(payload));
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            if (!(accessor.getUser() instanceof PanelPrincipal principal)) {
                throw new MessageDeliveryException("Suscripción sin autenticar");
            }
            String allowed = COMPANY_TOPIC_PREFIX + principal.payload().companyId();
            if (!allowed.equals(accessor.getDestination())) {
                throw new MessageDeliveryException("Suscripción no permitida a " + accessor.getDestination());
            }
        }
        return message;
    }

    public record PanelPrincipal(JwtService.JwtPayload payload) implements Principal {
        @Override
        public String getName() {
            return payload.userId().toString();
        }
    }
}
