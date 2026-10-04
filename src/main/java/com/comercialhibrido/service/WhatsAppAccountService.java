package com.comercialhibrido.service;

import com.comercialhibrido.config.WhatsAppProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Verifica contra la Graph API de Meta que un Phone Number ID y un token
 * funcionan, antes de guardarlos en la empresa.
 */
@Service
@RequiredArgsConstructor
public class WhatsAppAccountService {

    private final WhatsAppProperties whatsAppProperties;
    private final RestClient.Builder restClientBuilder;

    public Verification verificar(String phoneNumberId, String accessToken) {
        String token = accessToken != null && !accessToken.isBlank() ? accessToken : whatsAppProperties.accessToken();
        try {
            PhoneNumberInfo info = restClientBuilder.clone()
                .baseUrl(whatsAppProperties.apiBaseUrl())
                .defaultHeader("Authorization", "Bearer " + token)
                .build()
                .get()
                .uri(b -> b.path("/{id}").queryParam("fields", "display_phone_number,verified_name,quality_rating").build(phoneNumberId))
                .retrieve()
                .body(PhoneNumberInfo.class);
            if (info == null) {
                return Verification.error("Meta no devolvió datos del número.");
            }
            return new Verification(true, info.displayPhoneNumber(), info.verifiedName(), info.qualityRating(), null);
        } catch (RestClientResponseException e) {
            return Verification.error(mensajeMeta(e));
        } catch (Exception e) {
            return Verification.error("No se pudo contactar a Meta: " + e.getMessage());
        }
    }

    private static String mensajeMeta(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (e.getStatusCode().value() == 401 || body.contains("\"code\":190")) {
            return "El token de acceso no es válido o venció.";
        }
        if (e.getStatusCode().value() == 400 && body.contains("does not exist")) {
            return "Ese Phone Number ID no existe o el token no tiene acceso a él.";
        }
        return "Meta rechazó la verificación (HTTP " + e.getStatusCode().value() + ").";
    }

    public record Verification(boolean ok, String displayPhoneNumber, String verifiedName, String qualityRating, String error) {
        static Verification error(String message) {
            return new Verification(false, null, null, null, message);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PhoneNumberInfo(
        @JsonProperty("display_phone_number") String displayPhoneNumber,
        @JsonProperty("verified_name") String verifiedName,
        @JsonProperty("quality_rating") String qualityRating
    ) {}
}
