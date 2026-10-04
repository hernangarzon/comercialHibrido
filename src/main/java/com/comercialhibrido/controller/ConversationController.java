package com.comercialhibrido.controller;

import com.comercialhibrido.domain.entity.Conversation;
import com.comercialhibrido.domain.entity.Message;
import com.comercialhibrido.domain.enums.ConversationStatus;
import com.comercialhibrido.dto.SendMessageRequest;
import com.comercialhibrido.exception.IllegalTransitionException;
import com.comercialhibrido.repository.ConversationRepository;
import com.comercialhibrido.repository.MessageRepository;
import com.comercialhibrido.security.JwtService;
import com.comercialhibrido.service.ConversationStateService;
import com.comercialhibrido.service.OutgoingMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    // Atributo que PanelTokenFilter rellena con el JWT validado.
    private static final String AUTH_ATTRIBUTE = "authenticatedUser";

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ConversationStateService conversationStateService;
    private final OutgoingMessageService outgoingMessageService;

    /**
     * Lista conversaciones filtrando por estado.
     * La empresa sale siempre del JWT del usuario que inició sesión.
     */
    @GetMapping
    public ResponseEntity<List<ConversationSummaryResponse>> listarConversaciones(
        @RequestParam(value = "status", required = false) ConversationStatus status,
        @RequestAttribute(AUTH_ATTRIBUTE) JwtService.JwtPayload user
    ) {
        UUID companyId = user.companyId();
        List<Conversation> conversaciones = (status != null)
            ? conversationRepository.findByCompanyIdAndStatusOrderByUpdatedAtDesc(companyId, status)
            : conversationRepository.findByCompanyIdOrderByUpdatedAtDesc(companyId);

        List<ConversationSummaryResponse> response = conversaciones.stream()
            .map(c -> {
                Message ultimoMsg = messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(c.getId()).orElse(null);
                return ConversationSummaryResponse.from(c, ultimoMsg);
            })
            .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/mensajes")
    public ResponseEntity<List<MessageDetailResponse>> obtenerHistorial(
        @PathVariable("id") UUID conversationId,
        @RequestAttribute(AUTH_ATTRIBUTE) JwtService.JwtPayload user
    ) {
        verificarPertenencia(conversationId, user);
        List<Message> mensajes = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        List<MessageDetailResponse> response = mensajes.stream()
            .map(MessageDetailResponse::from)
            .toList();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/tomar-control")
    public ResponseEntity<ConversationSummaryResponse> tomarControl(
        @PathVariable("id") UUID conversationId,
        @RequestAttribute(AUTH_ATTRIBUTE) JwtService.JwtPayload user
    ) {
        verificarPertenencia(conversationId, user);
        // El comercial asignado es quien hace la petición, no un id enviado por el cliente.
        Conversation conversation = conversationStateService.tomarControl(conversationId, user.userId());
        return ResponseEntity.ok(ConversationSummaryResponse.from(conversation, null));
    }

    @PostMapping("/{id}/liberar-control")
    public ResponseEntity<ConversationSummaryResponse> liberarControl(
        @PathVariable("id") UUID conversationId,
        @RequestAttribute(AUTH_ATTRIBUTE) JwtService.JwtPayload user
    ) {
        verificarPertenencia(conversationId, user);
        Conversation conversation = conversationStateService.liberarControl(conversationId);
        return ResponseEntity.ok(ConversationSummaryResponse.from(conversation, null));
    }

    @PostMapping("/{id}/archivar")
    public ResponseEntity<ConversationSummaryResponse> archivar(
        @PathVariable("id") UUID conversationId,
        @RequestAttribute(AUTH_ATTRIBUTE) JwtService.JwtPayload user
    ) {
        verificarPertenencia(conversationId, user);
        Conversation conversation = conversationStateService.archivar(conversationId);
        return ResponseEntity.ok(ConversationSummaryResponse.from(conversation, null));
    }

    @PostMapping("/{id}/mensajes")
    public ResponseEntity<Void> enviarMensaje(
        @PathVariable("id") UUID conversationId,
        @Valid @RequestBody SendMessageRequest request,
        @RequestAttribute(AUTH_ATTRIBUTE) JwtService.JwtPayload user
    ) {
        verificarPertenencia(conversationId, user);
        outgoingMessageService.enviarMensajeComercial(conversationId, request.content());
        return ResponseEntity.accepted().build();
    }

    /**
     * Responde 404 (no 403) si la conversación es de otra empresa, para no revelar que existe.
     */
    private void verificarPertenencia(UUID conversationId, JwtService.JwtPayload user) {
        if (!conversationRepository.existsByIdAndCompanyId(conversationId, user.companyId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversación no encontrada");
        }
    }

    @ExceptionHandler(IllegalTransitionException.class)
    public ResponseEntity<String> manejarTransicionInvalida(IllegalTransitionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    public record ConversationSummaryResponse(
        UUID id,
        String customerName,
        String customerPhone,
        String status,
        Integer leadScore,
        String lastMessage,
        String lastMessageSender,
        Instant lastMessageAt,
        String summary,
        UUID assignedSalespersonId,
        Instant createdAt,
        Instant updatedAt
    ) {
        static ConversationSummaryResponse from(Conversation c, Message lastMessage) {
            String name = (c.getCustomer() != null && c.getCustomer().getDisplayName() != null) 
                ? c.getCustomer().getDisplayName() 
                : "Sin Nombre";
            String phone = (c.getCustomer() != null) ? c.getCustomer().getPhoneNumber() : "";

            return new ConversationSummaryResponse(
                c.getId(),
                name,
                phone,
                c.getStatus().name(),
                c.getLeadScore(),
                lastMessage != null ? lastMessage.getContent() : null,
                lastMessage != null ? lastMessage.getSender().name() : null,
                lastMessage != null ? lastMessage.getCreatedAt() : null,
                c.getSummary(),
                c.getAssignedSalespersonId(),
                c.getCreatedAt(),
                c.getUpdatedAt()
            );
        }
    }

    public record MessageDetailResponse(
        UUID id,
        String sender,
        String content,
        String mediaType,
        String mediaId,
        String mediaFilename,
        String deliveryStatus,
        String deliveryError,
        Instant createdAt
    ) {
        static MessageDetailResponse from(Message m) {
            return new MessageDetailResponse(
                m.getId(),
                m.getSender().name(),
                m.getContent(),
                m.getMediaType() != null ? m.getMediaType() : "TEXT",
                m.getMediaId(),
                m.getMediaFilename(),
                m.getDeliveryStatus() != null ? m.getDeliveryStatus().name() : null,
                m.getDeliveryError(),
                m.getCreatedAt()
            );
        }
    }
}