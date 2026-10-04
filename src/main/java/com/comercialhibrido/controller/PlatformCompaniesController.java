package com.comercialhibrido.controller;

import com.comercialhibrido.config.PlatformProperties;
import com.comercialhibrido.domain.entity.Company;
import com.comercialhibrido.domain.entity.SalesLead;
import com.comercialhibrido.domain.entity.User;
import com.comercialhibrido.repository.CompanyRepository;
import com.comercialhibrido.repository.ConversationRepository;
import com.comercialhibrido.repository.SalesLeadRepository;
import com.comercialhibrido.repository.UserRepository;
import com.comercialhibrido.security.JwtService;
import com.comercialhibrido.security.PanelTokenFilter;
import com.comercialhibrido.security.PasswordService;
import com.comercialhibrido.security.Roles;
import com.comercialhibrido.security.TemporaryPasswords;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Empresas clientes de la plataforma: alta (también a partir de una solicitud de la
 * landing) y suspensión. Solo para los dueños de la plataforma (PLATFORM_ADMIN_EMAILS).
 */
@RestController
@RequestMapping("/api/platform/companies")
@RequiredArgsConstructor
public class PlatformCompaniesController {

    private static final String AUTH = PanelTokenFilter.AUTH_ATTRIBUTE;

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final SalesLeadRepository salesLeadRepository;
    private final PasswordService passwordService;
    private final PlatformProperties platformProperties;

    @GetMapping
    @Transactional(readOnly = true)
    public List<CompanyResponse> listar(@RequestAttribute(AUTH) JwtService.JwtPayload auth) {
        requirePlatformAdmin(auth);
        return companyRepository.findAll().stream()
            .sorted(Comparator.comparing(Company::getCreatedAt).reversed())
            .map(c -> CompanyResponse.from(c,
                userRepository.countByCompanyId(c.getId()),
                conversationRepository.countByCompanyId(c.getId()),
                c.getId().equals(auth.companyId())))
            .toList();
    }

    /** Crea la empresa y su primer administrador con contraseña temporal (se muestra una sola vez). */
    @PostMapping
    @Transactional
    public NewClientResponse crear(@RequestAttribute(AUTH) JwtService.JwtPayload auth, @Valid @RequestBody NewClientRequest request) {
        requirePlatformAdmin(auth);
        String email = request.adminEmail().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un usuario con ese correo.");
        }
        SalesLead lead = request.leadId() == null ? null : salesLeadRepository.findById(request.leadId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));

        Company company = companyRepository.save(Company.builder().name(request.companyName().strip()).build());
        String temporal = TemporaryPasswords.generate();
        User admin = userRepository.save(User.builder()
            .company(company)
            .name(request.adminName().strip())
            .email(email)
            .role(Roles.ADMIN)
            .passwordHash(passwordService.hashPassword(temporal))
            .mustChangePassword(true)
            .build());
        if (lead != null) {
            lead.setStatus(SalesLead.CONVERTIDA);
            salesLeadRepository.save(lead);
        }
        return new NewClientResponse(
            CompanyResponse.from(company, 1, 0, false),
            new TeamController.InvitationResponse(TeamController.MemberResponse.from(admin), temporal)
        );
    }

    /** Suspende o reactiva una empresa: suspendida, nadie entra al panel y el bot deja de atenderla. */
    @PutMapping("/{id}")
    @Transactional
    public CompanyResponse actualizar(
        @RequestAttribute(AUTH) JwtService.JwtPayload auth,
        @PathVariable("id") UUID id,
        @RequestBody StatusRequest request
    ) {
        requirePlatformAdmin(auth);
        if (id.equals(auth.companyId()) && !request.active()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No puedes suspender la empresa con la que administras la plataforma.");
        }
        Company company = companyRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
        company.setActive(request.active());
        companyRepository.save(company);
        return CompanyResponse.from(company, userRepository.countByCompanyId(id), conversationRepository.countByCompanyId(id),
            id.equals(auth.companyId()));
    }

    private void requirePlatformAdmin(JwtService.JwtPayload user) {
        if (!platformProperties.isPlatformAdmin(user.email())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontrado");
        }
    }

    public record NewClientRequest(
        @NotBlank(message = "Escribe el nombre de la empresa") @Size(max = 100) String companyName,
        @NotBlank(message = "Escribe el nombre del administrador") @Size(max = 100) String adminName,
        @NotBlank(message = "Escribe el correo del administrador") @Email(message = "El correo no es válido") @Size(max = 150) String adminEmail,
        UUID leadId
    ) {}

    public record StatusRequest(boolean active) {}

    public record CompanyResponse(UUID id, String name, boolean active, boolean whatsappConnected,
                                  long users, long conversations, boolean yours, Instant createdAt) {
        static CompanyResponse from(Company c, long users, long conversations, boolean yours) {
            return new CompanyResponse(c.getId(), c.getName(), c.isActive(), c.getWhatsappPhoneNumberId() != null,
                users, conversations, yours, c.getCreatedAt());
        }
    }

    public record NewClientResponse(CompanyResponse company, TeamController.InvitationResponse invitation) {}
}
