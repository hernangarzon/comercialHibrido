package com.comercialhibrido.service;

import com.comercialhibrido.config.OpenAiProperties;
import com.comercialhibrido.dto.openai.OpenAiChatDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OpenAiChatClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatClient.class);
    private static final int MAX_INTENTOS = 4;
    private static final long MAX_ESPERA_SEGUNDOS = 20;

    private final OpenAiProperties openAiProperties;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    /**
     * Envia la lista de mensajes (system prompt + contexto + pregunta del
     * cliente) y devuelve la respuesta ya parseada como BotAnswer:
     * texto para el cliente + leadScore + si requiere escalamiento.
     *
     * Puede lanzar excepcion si OpenAI falla o el JSON no calza con el
     * schema (no deberia pasar con structured outputs, pero no se asume).
     * El llamador (BotResponseService, siguiente archivo) decide que
     * hacer si esto falla: por diseno, ante cualquier duda se escala al
     * humano en vez de arriesgar una respuesta inventada.
     */
    public BotAnswer generarRespuesta(List<ChatMessage> mensajes) {
        RestClient client = restClientBuilder.clone()
            .baseUrl(openAiProperties.apiBaseUrl())
            .defaultHeader("Authorization", "Bearer " + openAiProperties.apiKey())
            .build();

        ChatRequest request = new ChatRequest(
            openAiProperties.model(),
            mensajes,
            new ResponseFormat("json_schema", construirSchemaRespuestaBot()),
            0.3
        );

        ChatResponse response = enviarConReintentos(client, request);

        String contenidoJson = response.choices().get(0).message().content();

        try {
            return objectMapper.readValue(contenidoJson, BotAnswer.class);
        } catch (Exception e) {
            log.error("No se pudo parsear la respuesta estructurada del modelo: {}", contenidoJson, e);
            throw new IllegalStateException("Respuesta del LLM con formato inesperado.", e);
        }
    }

    /**
     * Reintenta ante límites de uso (429) y errores temporales del proveedor (5xx): el
     * plan gratuito de Groq limita los tokens por minuto y se libera en segundos. Así el
     * cliente recibe una respuesta real en lugar del mensaje de cortesía.
     */
    private ChatResponse enviarConReintentos(RestClient client, ChatRequest request) {
        for (int intento = 1; ; intento++) {
            try {
                return client.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                boolean temporal = status == 429 || status >= 500;
                if (!temporal || intento >= MAX_INTENTOS) {
                    throw e;
                }
                long espera = esperaSegundos(e, intento);
                log.warn("Proveedor de IA respondió {} (intento {}/{}); reintento en {} s.", status, intento, MAX_INTENTOS, espera);
                try {
                    Thread.sleep(espera * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    /** Usa Retry-After si el proveedor lo indica; si no, espera creciente (3 s, 6 s…), con tope. */
    private static long esperaSegundos(RestClientResponseException e, int intento) {
        String retryAfter = e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst("retry-after") : null;
        long sugerida = 3L * intento;
        if (retryAfter != null) {
            try {
                sugerida = (long) Math.ceil(Double.parseDouble(retryAfter.trim()));
            } catch (NumberFormatException ignored) {
                // Formato de fecha u otro: se usa la espera creciente.
            }
        }
        return Math.max(1, Math.min(sugerida, MAX_ESPERA_SEGUNDOS));
    }

    private JsonSchema construirSchemaRespuestaBot() {
        Map<String, Object> propiedades = Map.of(
            "respuesta", Map.of(
                "type", "string",
                "description", "Texto de respuesta para enviar al cliente por WhatsApp."
            ),
            "leadScore", Map.of(
                "type", "integer",
                "description", "Puntaje de intencion de compra de 0 a 100 segun esta conversacion."
            ),
            "requiereEscalamiento", Map.of(
                "type", "boolean",
                "description", "true SOLO si hay una venta concreta lista para cerrar, el cliente pide hablar con una persona o presenta un reclamo."
            ),
            "resumenParaAsesor", Map.of(
                "type", "string",
                "description", "Si requiereEscalamiento es true: resumen breve para el asesor (qué quiere, cantidades, fecha, lo que falta). Si es false: cadena vacía."
            )
        );

        Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", propiedades,
            "required", List.of("respuesta", "leadScore", "requiereEscalamiento", "resumenParaAsesor"),
            "additionalProperties", false
        );

        return new JsonSchema("respuesta_bot", true, schema);
    }
}