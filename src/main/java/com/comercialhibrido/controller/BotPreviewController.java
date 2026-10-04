package com.comercialhibrido.controller;

import com.comercialhibrido.domain.entity.Company;
import com.comercialhibrido.dto.openai.OpenAiChatDtos.BotAnswer;
import com.comercialhibrido.dto.openai.OpenAiChatDtos.ChatMessage;
import com.comercialhibrido.repository.CompanyRepository;
import com.comercialhibrido.security.JwtService;
import com.comercialhibrido.service.ConversationContextBuilder;
import com.comercialhibrido.service.OpenAiChatClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Probador del bot: responde como lo haría por WhatsApp, con la configuración
 * guardada de la empresa, pero sin crear conversaciones ni enviar nada.
 */
@RestController
@RequestMapping("/api/bot")
@RequiredArgsConstructor
public class BotPreviewController {

    private static final Logger log = LoggerFactory.getLogger(BotPreviewController.class);

    private final CompanyRepository companyRepository;
    private final ConversationContextBuilder contextBuilder;
    private final OpenAiChatClient openAiChatClient;

    @PostMapping("/preview")
    public BotAnswer probar(
        @RequestAttribute("authenticatedUser") JwtService.JwtPayload user,
        @Valid @RequestBody PreviewRequest request
    ) {
        Company company = companyRepository.findById(user.companyId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));

        List<ChatMessage> historial = request.messages().stream()
            .map(m -> new ChatMessage(m.role(), m.content()))
            .toList();
        try {
            return openAiChatClient.generarRespuesta(contextBuilder.construirPrueba(company, historial));
        } catch (Exception e) {
            log.warn("Fallo del modelo en el probador del bot: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "El modelo de IA no respondió (" + e.getMessage() + "). Revisa OPENAI_API_KEY.");
        }
    }

    public record PreviewRequest(
        @NotEmpty @Size(max = 30, message = "La prueba admite hasta 30 mensajes") List<@Valid PreviewMessage> messages
    ) {}

    public record PreviewMessage(
        @Pattern(regexp = "user|assistant") String role,
        @NotBlank @Size(max = 2_000) String content
    ) {}
}
