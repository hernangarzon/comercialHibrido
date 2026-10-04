package com.comercialhibrido.service;

import com.comercialhibrido.config.WhatsAppProperties;
import com.comercialhibrido.domain.entity.Company;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plantillas de WhatsApp aprobadas en la cuenta de WhatsApp Business (WABA) de la empresa.
 * Se consultan a Meta en cada uso: las aprueba o rechaza Meta, no este sistema.
 */
@Service
@RequiredArgsConstructor
public class WhatsAppTemplateService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\d+)}}");

    private final WhatsAppProperties whatsAppProperties;
    private final RestClient.Builder restClientBuilder;

    public List<Template> aprobadas(Company company) {
        String waba = company.getWhatsappBusinessAccountId();
        if (waba == null || waba.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Falta el ID de la cuenta de WhatsApp Business. Configúralo en Ajustes → WhatsApp.");
        }
        try {
            TemplatesPage page = cliente(company)
                .get()
                .uri(b -> b.path("/{waba}/message_templates")
                    .queryParam("fields", "name,language,status,category,components")
                    .queryParam("limit", 200)
                    .build(waba))
                .retrieve()
                .body(TemplatesPage.class);
            if (page == null || page.data() == null) return List.of();
            return page.data().stream()
                .filter(t -> "APPROVED".equalsIgnoreCase(t.status()))
                .map(WhatsAppTemplateService::toTemplate)
                .toList();
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Meta no devolvió las plantillas (HTTP " + e.getStatusCode().value() + "). Revisa el ID de la cuenta y el token.");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo contactar a Meta: " + e.getMessage());
        }
    }

    /** Busca una plantilla aprobada por nombre e idioma, o falla con un mensaje claro. */
    public Template buscar(Company company, String name, String language) {
        return aprobadas(company).stream()
            .filter(t -> t.name().equals(name) && t.language().equals(language))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La plantilla «" + name + "» (" + language + ") no existe o no está aprobada en Meta."));
    }

    /** Verifica que el ID de la cuenta exista y el token tenga acceso a sus plantillas. */
    public String verificarCuenta(String waba, String accessToken) {
        try {
            restClientBuilder.clone()
                .baseUrl(whatsAppProperties.apiBaseUrl())
                .defaultHeader("Authorization", "Bearer " + token(accessToken))
                .build()
                .get()
                .uri(b -> b.path("/{waba}/message_templates").queryParam("limit", 1).build(waba))
                .retrieve()
                .toBodilessEntity();
            return null;
        } catch (RestClientResponseException e) {
            return "Meta no reconoce ese ID de cuenta de WhatsApp Business o el token no tiene acceso a ella.";
        } catch (Exception e) {
            return "No se pudo contactar a Meta: " + e.getMessage();
        }
    }

    private RestClient cliente(Company company) {
        return restClientBuilder.clone()
            .baseUrl(whatsAppProperties.apiBaseUrl())
            .defaultHeader("Authorization", "Bearer " + token(company.getWhatsappAccessToken()))
            .build();
    }

    private String token(String companyToken) {
        return companyToken != null && !companyToken.isBlank() ? companyToken : whatsAppProperties.accessToken();
    }

    private static Template toTemplate(MetaTemplate t) {
        String body = t.components() == null ? "" : t.components().stream()
            .filter(c -> "BODY".equalsIgnoreCase(c.type()))
            .map(MetaComponent::text)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse("");
        int params = 0;
        Matcher m = PLACEHOLDER.matcher(body);
        while (m.find()) params = Math.max(params, Integer.parseInt(m.group(1)));
        return new Template(t.name(), t.language(), t.category(), body, params);
    }

    /**
     * @param body       texto del cuerpo con marcadores {{1}}, {{2}}…
     * @param paramCount cantidad de parámetros que exige el cuerpo
     */
    public record Template(String name, String language, String category, String body, int paramCount) {

        /** Texto final tal como lo verá el cliente, para guardarlo en el historial. */
        public String render(List<String> params) {
            Matcher m = PLACEHOLDER.matcher(body);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                int index = Integer.parseInt(m.group(1)) - 1;
                String value = params != null && index < params.size() ? params.get(index) : m.group();
                m.appendReplacement(sb, Matcher.quoteReplacement(value));
            }
            m.appendTail(sb);
            return sb.toString();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TemplatesPage(List<MetaTemplate> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MetaTemplate(String name, String language, String status, String category, List<MetaComponent> components) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MetaComponent(String type, String text) {}
}
