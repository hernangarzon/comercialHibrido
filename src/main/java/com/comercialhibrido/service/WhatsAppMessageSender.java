package com.comercialhibrido.service;

import com.comercialhibrido.config.WhatsAppProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WhatsAppMessageSender {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppMessageSender.class);

    private final WhatsAppProperties whatsAppProperties;
    private final RestClient.Builder restClientBuilder;

    /**
     * Envío estándar usando las credenciales por defecto (compatibilidad con Worker existente).
     */
    public String enviarTexto(String toPhoneNumber, String body) {
        return enviarTexto(
            whatsAppProperties.phoneNumberId(),
            whatsAppProperties.accessToken(),
            toPhoneNumber,
            body
        );
    }

    /**
     * Envío multiempresa: utiliza el Phone Number ID y Token de la empresa específica.
     */
    public String enviarTexto(String phoneNumberId, String accessToken, String toPhoneNumber, String body) {
        Map<String, Object> payload = Map.of(
            "messaging_product", "whatsapp",
            "to", toPhoneNumber,
            "type", "text",
            "text", Map.of("body", body)
        );
        return enviar(phoneNumberId, accessToken, toPhoneNumber, payload);
    }

    /**
     * Envía una plantilla aprobada. Es la única forma de escribirle a un cliente
     * cuando pasaron más de 24 h desde su último mensaje (política de Meta).
     */
    public String enviarPlantilla(String phoneNumberId, String accessToken, String toPhoneNumber,
                                  String templateName, String language, List<String> bodyParams) {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", templateName);
        template.put("language", Map.of("code", language));
        if (bodyParams != null && !bodyParams.isEmpty()) {
            template.put("components", List.of(Map.of(
                "type", "body",
                "parameters", bodyParams.stream().map(p -> Map.of("type", "text", "text", p)).toList()
            )));
        }
        Map<String, Object> payload = Map.of(
            "messaging_product", "whatsapp",
            "to", toPhoneNumber,
            "type", "template",
            "template", template
        );
        return enviar(phoneNumberId, accessToken, toPhoneNumber, payload);
    }

    private String enviar(String phoneNumberId, String accessToken, String toPhoneNumber, Map<String, Object> payload) {
        String targetPhoneId = (phoneNumberId != null && !phoneNumberId.isBlank())
            ? phoneNumberId
            : whatsAppProperties.phoneNumberId();

        String token = (accessToken != null && !accessToken.isBlank())
            ? accessToken
            : whatsAppProperties.accessToken();

        RestClient client = restClientBuilder.clone()
            .baseUrl(whatsAppProperties.apiBaseUrl())
            .defaultHeader("Authorization", "Bearer " + token)
            .build();

        log.info("Enviando mensaje ({}) a {} desde PhoneId {}", payload.get("type"), toPhoneNumber, targetPhoneId);

        WhatsAppSendResponse response = client.post()
            .uri("/{phoneNumberId}/messages", targetPhoneId)
            .body(payload)
            .retrieve()
            .body(WhatsAppSendResponse.class);

        if (response != null && response.messages() != null && !response.messages().isEmpty()) {
            String wamid = response.messages().getFirst().id();
            log.info("Mensaje entregado exitosamente a Meta con WAMID: {}", wamid);
            return wamid;
        }

        throw new IllegalStateException("Meta no devolvio un identificador de mensaje valido.");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WhatsAppSendResponse(
        @JsonProperty("messaging_product") String messagingProduct,
        List<MessageRef> messages
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MessageRef(String id) {}
}