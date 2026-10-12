package com.comercialhibrido.service;

import com.comercialhibrido.config.WhatsAppProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class WhatsAppMediaService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppMediaService.class);

    private final WhatsAppProperties whatsAppProperties;
    private final RestClient.Builder restClientBuilder;

    /**
     * @param accessToken token de la empresa dueña del archivo; si es nulo se usa el global.
     */
    public ResponseEntity<byte[]> descargarArchivo(String mediaId, String accessToken) {
        try {
            MediaFile file = descargar(mediaId, accessToken);
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_TYPE, file.mimeType());
            return ResponseEntity.ok().headers(headers).body(file.bytes());
        } catch (Exception e) {
            log.error("Error al descargar archivo multimedia {} de Meta: {}", mediaId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /** Descarga un archivo recibido por WhatsApp (dos pasos: URL temporal y luego los bytes). */
    public MediaFile descargar(String mediaId, String accessToken) {
        String token = (accessToken != null && !accessToken.isBlank())
            ? accessToken
            : whatsAppProperties.accessToken();
        RestClient client = restClientBuilder.clone()
            .baseUrl(whatsAppProperties.apiBaseUrl())
            .defaultHeader("Authorization", "Bearer " + token)
            .build();

        // 1. Obtener la URL temporal del archivo desde Meta
        MediaUrlResponse metaMedia = client.get()
            .uri("/{mediaId}", mediaId)
            .retrieve()
            .body(MediaUrlResponse.class);

        if (metaMedia == null || metaMedia.url() == null) {
            throw new IllegalStateException("Meta no devolvió la URL del archivo " + mediaId);
        }

        // 2. Descargar los bytes reales del archivo
        RestClient downloadClient = restClientBuilder.clone().build();
        byte[] fileBytes = downloadClient.get()
            .uri(metaMedia.url())
            .header("Authorization", "Bearer " + token)
            .retrieve()
            .body(byte[].class);

        if (fileBytes == null) {
            throw new IllegalStateException("El archivo " + mediaId + " llegó vacío");
        }
        return new MediaFile(fileBytes, metaMedia.mimeType() != null ? metaMedia.mimeType() : "application/octet-stream");
    }

    public record MediaFile(byte[] bytes, String mimeType) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MediaUrlResponse(String url, String mime_type, String id) {
        public String mimeType() { return mime_type; }
    }
}