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

    public ResponseEntity<byte[]> descargarArchivo(String mediaId) {
        try {
            RestClient client = restClientBuilder
                .baseUrl(whatsAppProperties.apiBaseUrl())
                .defaultHeader("Authorization", "Bearer " + whatsAppProperties.accessToken())
                .build();

            // 1. Obtener la URL temporal del archivo desde Meta
            MediaUrlResponse metaMedia = client.get()
                .uri("/{mediaId}", mediaId)
                .retrieve()
                .body(MediaUrlResponse.class);

            if (metaMedia == null || metaMedia.url() == null) {
                return ResponseEntity.notFound().build();
            }

            // 2. Descargar los bytes reales del archivo
            RestClient downloadClient = restClientBuilder.build();
            byte[] fileBytes = downloadClient.get()
                .uri(metaMedia.url())
                .header("Authorization", "Bearer " + whatsAppProperties.accessToken())
                .retrieve()
                .body(byte[].class);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_TYPE, metaMedia.mimeType() != null ? metaMedia.mimeType() : "application/octet-stream");

            return ResponseEntity.ok()
                .headers(headers)
                .body(fileBytes);

        } catch (Exception e) {
            log.error("Error al descargar archivo multimedia {} de Meta: {}", mediaId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MediaUrlResponse(String url, String mime_type, String id) {
        public String mimeType() { return mime_type; }
    }
}