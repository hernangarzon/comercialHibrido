package com.comercialhibrido.service;

import com.comercialhibrido.config.OpenAiProperties;
import com.comercialhibrido.domain.entity.Conversation;
import com.comercialhibrido.domain.entity.Message;
import com.comercialhibrido.domain.enums.MessageSender;
import com.comercialhibrido.repository.MessageRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * Transcribe las notas de voz de los clientes con Whisper (API compatible con OpenAI,
 * por defecto Groq) para que el bot y el asesor puedan leer lo que dijeron.
 * Se ejecuta en el worker, antes de que el bot responda.
 */
@Service
@RequiredArgsConstructor
public class VoiceTranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(VoiceTranscriptionService.class);
    private static final int MAX_INTENTOS = 3;

    private final MessageRepository messageRepository;
    private final WhatsAppMediaService mediaService;
    private final OpenAiProperties openAiProperties;
    private final RestClient.Builder restClientBuilder;

    @Value("${openai.transcription-model:whisper-large-v3-turbo}")
    private String model = "whisper-large-v3-turbo";

    /** Idioma esperado: mejora la precisión y la velocidad de Whisper. */
    @Value("${openai.transcription-language:es}")
    private String language = "es";

    /** Transcribe las notas de voz pendientes de la conversación (normalmente la recién llegada). */
    public void transcribirPendientes(Conversation conversation) {
        List<Message> pendientes = messageRepository.findByConversationIdAndSenderAndMediaTypeAndContent(
            conversation.getId(), MessageSender.CLIENTE, VoiceNotes.MEDIA_TYPE, VoiceNotes.PENDING);
        for (Message audio : pendientes) {
            audio.setContent(transcribir(audio, conversation.getCompany().getWhatsappAccessToken()));
            messageRepository.save(audio);
        }
    }

    private String transcribir(Message audio, String companyToken) {
        try {
            WhatsAppMediaService.MediaFile file = mediaService.descargar(audio.getMediaId(), companyToken);
            String text = enviarAWhisper(file);
            if (text == null || text.isBlank()) {
                log.warn("La nota de voz {} no tiene voz reconocible.", audio.getId());
                return VoiceNotes.FAILED;
            }
            return text.strip();
        } catch (Exception e) {
            log.error("No se pudo transcribir la nota de voz {}: {}", audio.getId(), e.getMessage());
            return VoiceNotes.FAILED;
        }
    }

    private String enviarAWhisper(WhatsAppMediaService.MediaFile file) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", archivo(file));
        form.add("model", model);
        form.add("language", language);
        form.add("response_format", "json");

        RestClient client = restClientBuilder.clone()
            .baseUrl(openAiProperties.apiBaseUrl())
            .defaultHeader("Authorization", "Bearer " + openAiProperties.apiKey())
            .build();

        for (int intento = 1; ; intento++) {
            try {
                Transcription result = client.post()
                    .uri("/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(Transcription.class);
                return result != null ? result.text() : null;
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if ((status != 429 && status < 500) || intento >= MAX_INTENTOS) {
                    throw e;
                }
                log.warn("Transcripción: el proveedor respondió {} (intento {}/{}), reintentando.", status, intento, MAX_INTENTOS);
                try {
                    Thread.sleep(2000L * intento);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    /** Parte "file" del formulario: Whisper deduce el formato por la extensión del nombre. */
    private static HttpEntity<ByteArrayResource> archivo(WhatsAppMediaService.MediaFile file) {
        String extension = extension(file.mimeType());
        ByteArrayResource resource = new ByteArrayResource(file.bytes()) {
            @Override
            public String getFilename() {
                return "nota-de-voz." + extension;
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.mimeType().split(";")[0].trim()));
        return new HttpEntity<>(resource, headers);
    }

    private static String extension(String mimeType) {
        String base = mimeType.split(";")[0].trim().toLowerCase();
        return switch (base) {
            case "audio/mpeg", "audio/mp3" -> "mp3";
            case "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a";
            case "audio/aac" -> "aac";
            case "audio/amr" -> "amr";
            case "audio/wav", "audio/x-wav" -> "wav";
            case "audio/webm" -> "webm";
            default -> "ogg"; // las notas de voz de WhatsApp son audio/ogg (opus)
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Transcription(String text) {}
}
