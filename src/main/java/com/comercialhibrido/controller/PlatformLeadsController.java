package com.comercialhibrido.controller;

import com.comercialhibrido.config.PlatformProperties;
import com.comercialhibrido.domain.entity.SalesLead;
import com.comercialhibrido.repository.SalesLeadRepository;
import com.comercialhibrido.security.JwtService;
import com.comercialhibrido.security.PanelTokenFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Solicitudes de demo de la landing. Solo para los dueños de la plataforma
 * (PLATFORM_ADMIN_EMAILS), no para los administradores de cada empresa cliente.
 */
@RestController
@RequestMapping("/api/platform/leads")
@RequiredArgsConstructor
public class PlatformLeadsController {

    private final SalesLeadRepository salesLeadRepository;
    private final PlatformProperties platformProperties;

    @GetMapping
    public List<LeadResponse> listar(@RequestAttribute(PanelTokenFilter.AUTH_ATTRIBUTE) JwtService.JwtPayload user) {
        requirePlatformAdmin(user);
        return salesLeadRepository.findTop200ByOrderByCreatedAtDesc().stream().map(LeadResponse::from).toList();
    }

    @PutMapping("/{id}")
    public LeadResponse actualizar(
        @RequestAttribute(PanelTokenFilter.AUTH_ATTRIBUTE) JwtService.JwtPayload user,
        @PathVariable("id") UUID id,
        @Valid @RequestBody StatusRequest request
    ) {
        requirePlatformAdmin(user);
        SalesLead lead = salesLeadRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));
        lead.setStatus(request.status());
        return LeadResponse.from(salesLeadRepository.saveAndFlush(lead));
    }

    private void requirePlatformAdmin(JwtService.JwtPayload user) {
        if (!platformProperties.isPlatformAdmin(user.email())) {
            // 404 en lugar de 403: los usuarios de las empresas no necesitan saber que existe.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontrado");
        }
    }

    public record StatusRequest(
        @Pattern(regexp = SalesLead.NUEVA + "|" + SalesLead.CONTACTADA + "|" + SalesLead.DESCARTADA, message = "Estado no válido")
        String status
    ) {}

    public record LeadResponse(UUID id, String name, String companyName, String email, String phone,
                               String message, String status, Instant createdAt) {
        static LeadResponse from(SalesLead l) {
            return new LeadResponse(l.getId(), l.getName(), l.getCompanyName(), l.getEmail(), l.getPhone(),
                l.getMessage(), l.getStatus(), l.getCreatedAt());
        }
    }
}
