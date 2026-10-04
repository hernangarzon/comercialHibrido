package com.comercialhibrido.controller;

import com.comercialhibrido.config.PlatformProperties;
import com.comercialhibrido.domain.entity.Company;
import com.comercialhibrido.repository.CompanyRepository;
import com.comercialhibrido.repository.MessageRepository;
import com.comercialhibrido.repository.ProductRepository;
import com.comercialhibrido.repository.UserRepository;
import com.comercialhibrido.security.JwtService;
import com.comercialhibrido.security.PanelTokenFilter;
import com.comercialhibrido.security.Roles;
import com.comercialhibrido.service.WhatsAppAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Instant;
import java.util.List;

/**
 * Configuración de la empresa del usuario: conocimiento del bot, marca blanca,
 * conexión de WhatsApp y lista de puesta en marcha.
 */
@RestController
@RequestMapping("/api/company")
@RequiredArgsConstructor
public class CompanySettingsController {

    private static final String AUTH = PanelTokenFilter.AUTH_ATTRIBUTE;
    private static final int MIN_KNOWLEDGE_CHARS = 80;
    /** ~200 KB de imagen en base64. */
    private static final int MAX_LOGO_CHARS = 280_000;

    private final CompanyRepository companyRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final WhatsAppAccountService whatsAppAccountService;
    private final PlatformProperties platformProperties;

    @GetMapping
    public CompanySettingsResponse obtener(@RequestAttribute(AUTH) JwtService.JwtPayload user) {
        return CompanySettingsResponse.from(empresa(user));
    }

    @PutMapping("/knowledge")
    public CompanySettingsResponse actualizarConocimiento(
        @RequestAttribute(AUTH) JwtService.JwtPayload user,
        @Valid @RequestBody KnowledgeRequest request
    ) {
        Roles.requireAdmin(user);
        Company company = empresa(user);
        company.setKnowledgeBase(blankToNull(request.knowledgeBase()));
        company.setCustomPrompt(blankToNull(request.customPrompt()));
        return CompanySettingsResponse.from(companyRepository.saveAndFlush(company));
    }

    @PutMapping("/branding")
    public CompanySettingsResponse actualizarMarca(
        @RequestAttribute(AUTH) JwtService.JwtPayload user,
        @Valid @RequestBody BrandingRequest request
    ) {
        Roles.requireAdmin(user);
        String color = blankToNull(request.brandColor());
        if (color != null && contrastWithWhite(color) < 4.5) {
            // Los botones usan texto blanco sobre el color de marca.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Ese color es muy claro: el texto blanco no se leería bien. Elige un tono más oscuro.");
        }
        String logo = blankToNull(request.logoDataUrl());
        if (logo != null && logo.length() > MAX_LOGO_CHARS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El logo es muy pesado. Usa una imagen de menos de 200 KB.");
        }
        Company company = empresa(user);
        company.setBrandColor(color == null ? null : color.toLowerCase());
        company.setLogoDataUrl(logo);
        return CompanySettingsResponse.from(companyRepository.saveAndFlush(company));
    }

    /** Verifica el número contra Meta y, si funciona, lo guarda en la empresa. */
    @PutMapping("/whatsapp")
    public WhatsAppResponse conectarWhatsapp(
        @RequestAttribute(AUTH) JwtService.JwtPayload user,
        @Valid @RequestBody WhatsAppRequest request
    ) {
        Roles.requireAdmin(user);
        Company company = empresa(user);
        String phoneNumberId = request.phoneNumberId().trim();
        companyRepository.findByWhatsappPhoneNumberIdAndActiveTrue(phoneNumberId)
            .filter(other -> !other.getId().equals(company.getId()))
            .ifPresent(other -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese número ya está conectado a otra empresa.");
            });

        String token = blankToNull(request.accessToken());
        String tokenToUse = token != null ? token : company.getWhatsappAccessToken();
        WhatsAppAccountService.Verification verification = whatsAppAccountService.verificar(phoneNumberId, tokenToUse);
        if (!verification.ok()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, verification.error());
        }
        company.setWhatsappPhoneNumberId(phoneNumberId);
        if (token != null) {
            company.setWhatsappAccessToken(token);
        }
        companyRepository.saveAndFlush(company);
        return new WhatsAppResponse(phoneNumberId, company.getWhatsappAccessToken() != null, verification);
    }

    @PostMapping("/whatsapp/test")
    public WhatsAppResponse probarWhatsapp(@RequestAttribute(AUTH) JwtService.JwtPayload user) {
        Company company = empresa(user);
        return new WhatsAppResponse(
            company.getWhatsappPhoneNumberId(),
            company.getWhatsappAccessToken() != null,
            whatsAppAccountService.verificar(company.getWhatsappPhoneNumberId(), company.getWhatsappAccessToken())
        );
    }

    @GetMapping("/onboarding")
    public OnboardingResponse puestaEnMarcha(@RequestAttribute(AUTH) JwtService.JwtPayload user, HttpServletRequest request) {
        Company company = empresa(user);
        Instant lastClientMessage = messageRepository.lastClientMessageAt(company.getId());
        long products = productRepository.countByCompanyIdAndAvailableTrue(company.getId());
        long members = userRepository.countByCompanyIdAndActiveTrue(company.getId());
        int knowledge = company.getKnowledgeBase() == null ? 0 : company.getKnowledgeBase().length();

        List<Step> steps = List.of(
            new Step("whatsapp", "Conectar el número de WhatsApp", company.getWhatsappPhoneNumberId() != null,
                "Phone Number ID " + company.getWhatsappPhoneNumberId()),
            new Step("mensajes", "Recibir el primer mensaje de un cliente", lastClientMessage != null,
                lastClientMessage != null ? "Ya llegan mensajes de clientes." : "Escribe al número desde otro teléfono para probar."),
            new Step("conocimiento", "Cargar la base de conocimiento", knowledge >= MIN_KNOWLEDGE_CHARS,
                knowledge + " caracteres"),
            new Step("catalogo", "Agregar productos al catálogo", products > 0, products + " disponibles"),
            new Step("equipo", "Invitar a tu equipo comercial", members > 1, members + " usuarios activos"),
            new Step("marca", "Personalizar la marca del panel", company.getBrandColor() != null || company.getLogoDataUrl() != null,
                "Opcional")
        );

        // El webhook y su token son de la plataforma (una app de Meta para todas las empresas):
        // solo se muestran a sus administradores.
        PlatformWebhook webhook = platformProperties.isPlatformAdmin(user.email())
            ? new PlatformWebhook(ServletUriComponentsBuilder.fromContextPath(request).path("/webhook/whatsapp").toUriString())
            : null;
        return new OnboardingResponse(steps, webhook);
    }

    private Company empresa(JwtService.JwtPayload user) {
        return companyRepository.findById(user.companyId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Contraste WCAG entre un color #rrggbb y el blanco. */
    static double contrastWithWhite(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        double r = channel((rgb >> 16) & 0xff), g = channel((rgb >> 8) & 0xff), b = channel(rgb & 0xff);
        double luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return 1.05 / (luminance + 0.05);
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    public record KnowledgeRequest(
        @Size(max = 20_000, message = "La base de conocimiento admite hasta 20.000 caracteres") String knowledgeBase,
        @Size(max = 4_000, message = "Las directrices admiten hasta 4.000 caracteres") String customPrompt
    ) {}

    public record BrandingRequest(
        @Pattern(regexp = "^$|^#[0-9a-fA-F]{6}$", message = "El color debe tener el formato #rrggbb") String brandColor,
        @Pattern(regexp = "^$|^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$",
            message = "El logo debe ser una imagen PNG, JPG o WebP") String logoDataUrl
    ) {}

    public record WhatsAppRequest(
        @NotBlank(message = "El Phone Number ID es obligatorio")
        @Pattern(regexp = "^\\s*\\d{6,30}\\s*$", message = "El Phone Number ID solo tiene números") String phoneNumberId,
        @Size(max = 500) String accessToken
    ) {}

    public record WhatsAppResponse(String phoneNumberId, boolean hasOwnToken, WhatsAppAccountService.Verification verification) {}

    public record Step(String key, String title, boolean done, String detail) {}

    public record PlatformWebhook(String callbackUrl) {}

    public record OnboardingResponse(List<Step> steps, PlatformWebhook platformWebhook) {}

    public record CompanySettingsResponse(
        String name,
        String whatsappPhoneNumberId,
        boolean hasOwnWhatsappToken,
        String knowledgeBase,
        String customPrompt,
        String brandColor,
        String logoDataUrl,
        Instant updatedAt
    ) {
        static CompanySettingsResponse from(Company c) {
            return new CompanySettingsResponse(
                c.getName(),
                c.getWhatsappPhoneNumberId(),
                c.getWhatsappAccessToken() != null && !c.getWhatsappAccessToken().isBlank(),
                c.getKnowledgeBase(),
                c.getCustomPrompt(),
                c.getBrandColor(),
                c.getLogoDataUrl(),
                c.getUpdatedAt()
            );
        }
    }
}
