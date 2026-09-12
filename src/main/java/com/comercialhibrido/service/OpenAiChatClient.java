package com.comercialhibrido.service;

import com.comercialhibrido.config.OpenAiProperties;
import com.comercialhibrido.dto.openai.OpenAiChatDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OpenAiChatClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatClient.class);

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
        RestClient client = restClientBuilder
            .baseUrl(openAiProperties.apiBaseUrl())
            .defaultHeader("Authorization", "Bearer " + openAiProperties.apiKey())
            .build();

        ChatRequest request = new ChatRequest(
            openAiProperties.model(),
            mensajes,
            new ResponseFormat("json_schema", construirSchemaRespuestaBot()),
            0.3
        );

        ChatResponse response = client.post()
            .uri("/chat/completions")
            .body(request)
            .retrieve()
            .body(ChatResponse.class);

        String contenidoJson = response.choices().get(0).message().content();

        try {
            return objectMapper.readValue(contenidoJson, BotAnswer.class);
        } catch (Exception e) {
            log.error("No se pudo parsear la respuesta estructurada del modelo: {}", contenidoJson, e);
            throw new IllegalStateException("Respuesta del LLM con formato inesperado.", e);
        }
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
                "description", "true si la pregunta no se puede responder con confianza con el contexto dado."
            )
        );

        Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", propiedades,
            "required", List.of("respuesta", "leadScore", "requiereEscalamiento"),
            "additionalProperties", false
        );

        return new JsonSchema("respuesta_bot", true, schema);
    }
}