package com.comercialhibrido.controller;

import com.comercialhibrido.config.WhatsAppProperties;
import com.comercialhibrido.domain.entity.Conversation;
import com.comercialhibrido.dto.whatsapp.WhatsAppWebhookPayload;
import com.comercialhibrido.service.IncomingMessageService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/webhook/whatsapp")
@RequiredArgsConstructor
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final WhatsAppProperties whatsAppProperties;
    private final IncomingMessageService incomingMessageService;

    @GetMapping
    public ResponseEntity<String> verify(
        @RequestParam("hub.mode") String mode,
        @RequestParam("hub.verify_token") String verifyToken,
        @RequestParam("hub.challenge") String challenge
    ) {
        boolean modeValido = "subscribe".equals(mode);
        boolean tokenValido = whatsAppProperties.webhookVerifyToken().equals(verifyToken);

        if (modeValido && tokenValido) {
            log.info("Webhook de WhatsApp verificado correctamente.");
            return ResponseEntity.ok(challenge);
        }

        log.warn("Intento de verificacion de webhook fallido (mode={}, tokenValido={}).",
            mode, tokenValido);
        return ResponseEntity.status(403).build();
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody WhatsAppWebhookPayload payload) {
        if (payload.entry() == null) {
            return ResponseEntity.ok().build();
        }

        for (WhatsAppWebhookPayload.Entry entry : payload.entry()) {
            if (entry.changes() == null) continue;

            for (WhatsAppWebhookPayload.Change change : entry.changes()) {
                procesarChange(change.value());
            }
        }

        // Responde 200 OK inmediatamente a Meta para evitar reintentos y timeouts
        return ResponseEntity.ok().build();
    }

    private void procesarChange(WhatsAppWebhookPayload.ChangeValue value) {
        if (value == null || value.messages() == null) {
            return;
        }

        // Obtenemos el Phone Number ID de la empresa destinataria
        String targetPhoneNumberId = value.metadata() != null ? value.metadata().phoneNumberId() : null;
        if (targetPhoneNumberId == null) {
            log.warn("Webhook recibido sin metadata.phone_number_id");
            return;
        }

        List<WhatsAppWebhookPayload.Contact> contacts = value.contacts();

        for (WhatsAppWebhookPayload.IncomingMessage message : value.messages()) {
            try {
                String profileName = buscarNombrePerfil(contacts, message.from());

                // Persiste el mensaje y encola el trabajo para que MessageJobWorker lo procese
                incomingMessageService.processIncomingTextMessage(
                    targetPhoneNumberId,
                    message.from(),
                    profileName,
                    message
                );

            } catch (Exception e) {
                log.error("Error procesando mensaje entrante {}: {}", message.id(), e.getMessage(), e);
            }
        }
    }

    private String buscarNombrePerfil(List<WhatsAppWebhookPayload.Contact> contacts, String waId) {
        if (contacts == null) return null;
        return contacts.stream()
            .filter(c -> waId.equals(c.waId()))
            .map(c -> c.profile() != null ? c.profile().name() : null)
            .findFirst()
            .orElse(null);
    }
}