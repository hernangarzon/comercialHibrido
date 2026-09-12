package com.comercialhibrido.dto.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class OpenAiChatDtos {

    public record ChatRequest(
        String model,
        List<ChatMessage> messages,
        @JsonProperty("response_format") ResponseFormat responseFormat,
        Double temperature
    ) {}

    public record ChatMessage(
        String role,
        String content
    ) {}

    public record ResponseFormat(
        String type,
        @JsonProperty("json_schema") JsonSchema jsonSchema
    ) {}

    public record JsonSchema(
        String name,
        Boolean strict,
        java.util.Map<String, Object> schema
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatResponse(
        List<Choice> choices
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
        ChatMessage message
    ) {}

    public record BotAnswer(
        String respuesta,
        Integer leadScore,
        Boolean requiereEscalamiento
    ) {}
}
