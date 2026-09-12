package com.comercialhibrido.controller;

import com.comercialhibrido.domain.entity.Conversation;
import com.comercialhibrido.domain.entity.Message;
import com.comercialhibrido.domain.enums.ConversationStatus;
import com.comercialhibrido.dto.SendMessageRequest;
import com.comercialhibrido.dto.TomarControlRequest;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ConversationStateService conversationStateService;
    private final OutgoingMessageService outgoingMessageService;

    /**
     * Lista conversaciones filtrando por estado.
     * Toma automáticamente la empresa (companyId) del usuario que inició sesión.
     */
    @GetMapping
    public ResponseEntity<List<ConversationSummaryResponse>> listarConversaciones(
        @RequestParam(value = "status", required = false) ConversationStatus status,
        @RequestParam(value = "companyId", required = false) UUID paramCompanyId,
        @RequestAttribute(value = "authenticatedUser", required = false) JwtService.JwtPayload user
    ) {
        // Prioriza la empresa del usuario logueado, o usa el parámetro si existe
        UUID companyId = (user != null) ? user.companyId() : paramCompanyId;
        List<Conversation> conversaciones;

        if (companyId != null && status != null) {
            conversaciones = conversationRepository.findByCompanyIdAndStatusOrderByUpdatedAtDesc(companyId, status);
        } else if (companyId != null) {
            conversaciones = conversationRepository.findByCompanyIdOrderByUpdatedAtDesc(companyId);
        } else if (status != null) {
            conversaciones = conversationRepository.findByStatusOrderByUpdatedAtDesc(status);
        } else {
            conversaciones = conversationRepository.findAll();
        }

        List<ConversationSummaryResponse> response = conversaciones.stream()
            .map(c -> {
                Message ultimoMsg = messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(c.getId()).orElse(null);
                String preview = ultimoMsg != null ? ultimoMsg.getContent() : "";
                return ConversationSummaryResponse.from(c, preview);
            })
            .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/mensajes")
    public ResponseEntity<List<MessageDetailResponse>> obtenerHistorial(
        @PathVariable("id") UUID conversationId
    ) {
        List<Message> mensajes = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        List<MessageDetailResponse> response = mensajes.stream()
            .map(MessageDetailResponse::from)
            .toList();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/tomar-control")
    public ResponseEntity<ConversationSummaryResponse> tomarControl(
        @PathVariable("id") UUID conversationId,
        @RequestBody(required = false) TomarControlRequest request
    ) {
        UUID salespersonId = request != null ? request.salespersonId() : null;
        Conversation conversation = conversationStateService.tomarControl(conversationId, salespersonId);
        return ResponseEntity.ok(ConversationSummaryResponse.from(conversation, null));
    }

    @PostMapping("/{id}/liberar-control")
    public ResponseEntity<ConversationSummaryResponse> liberarControl(
        @PathVariable("id") UUID conversationId
    ) {
        Conversation conversation = conversationStateService.liberarControl(conversationId);
        return ResponseEntity.ok(ConversationSummaryResponse.from(conversation, null));
    }

    @PostMapping("/{id}/archivar")
    public ResponseEntity<ConversationSummaryResponse> archivar(
        @PathVariable("id") UUID conversationId
    ) {
        Conversation conversation = conversationStateService.archivar(conversationId);
        return ResponseEntity.ok(ConversationSummaryResponse.from(conversation, null));
    }

    @PostMapping("/{id}/mensajes")
    public ResponseEntity<Void> enviarMensaje(
        @PathVariable("id") UUID conversationId,
        @Valid @RequestBody SendMessageRequest request
    ) {
        outgoingMessageService.enviarMensajeComercial(conversationId, request.content());
        return ResponseEntity.accepted().build();
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
        Instant updatedAt
    ) {
        static ConversationSummaryResponse from(Conversation c, String lastMessage) {
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
                lastMessage,
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
                m.getCreatedAt()
            );
        }
    }
}