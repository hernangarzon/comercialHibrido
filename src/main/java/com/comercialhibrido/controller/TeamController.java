package com.comercialhibrido.controller;

import com.comercialhibrido.domain.entity.User;
import com.comercialhibrido.repository.CompanyRepository;
import com.comercialhibrido.repository.UserRepository;
import com.comercialhibrido.security.JwtService;
import com.comercialhibrido.security.PanelTokenFilter;
import com.comercialhibrido.security.PasswordService;
import com.comercialhibrido.security.Roles;
import com.comercialhibrido.security.TemporaryPasswords;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Equipo de la empresa. Solo ADMIN gestiona; cualquier usuario puede ver la lista.
 *
 * No hay envío de correos: la contraseña temporal se muestra una única vez al admin
 * para que la comparta, y el usuario debe cambiarla en su primer ingreso.
 */
@RestController
@RequestMapping("/api/team")
@RequiredArgsConstructor
public class TeamController {

    private static final String AUTH = PanelTokenFilter.AUTH_ATTRIBUTE;

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordService passwordService;

    @GetMapping
    public List<MemberResponse> listar(@RequestAttribute(AUTH) JwtService.JwtPayload auth) {
        return userRepository.findByCompanyIdOrderByNameAsc(auth.companyId()).stream()
            .map(MemberResponse::from)
            .toList();
    }

    @PostMapping
    @Transactional
    public InvitationResponse invitar(@RequestAttribute(AUTH) JwtService.JwtPayload auth, @Valid @RequestBody InviteRequest request) {
        Roles.requireAdmin(auth);
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un usuario con ese correo.");
        }
        String temporal = TemporaryPasswords.generate();
        User user = userRepository.save(User.builder()
            .company(companyRepository.getReferenceById(auth.companyId()))
            .name(request.name().strip())
            .email(email)
            .role(request.role().toUpperCase())
            .passwordHash(passwordService.hashPassword(temporal))
            .mustChangePassword(true)
            .build());
        return new InvitationResponse(MemberResponse.from(user), temporal);
    }

    @PutMapping("/{id}")
    @Transactional
    public MemberResponse actualizar(
        @RequestAttribute(AUTH) JwtService.JwtPayload auth,
        @PathVariable("id") UUID id,
        @Valid @RequestBody UpdateMemberRequest request
    ) {
        Roles.requireAdmin(auth);
        User user = miembro(id, auth);
        String newRole = request.role().toUpperCase();
        boolean pierdeAdmin = Roles.ADMIN.equalsIgnoreCase(user.getRole()) && user.isActive()
            && (!request.active() || !Roles.ADMIN.equals(newRole));

        if (user.getId().equals(auth.userId()) && (pierdeAdmin || !request.active())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "No puedes quitarte el rol de administrador ni desactivar tu propia cuenta.");
        }
        if (pierdeAdmin && userRepository.countByCompanyIdAndRoleIgnoreCaseAndActiveTrue(auth.companyId(), Roles.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La empresa debe tener al menos un administrador activo.");
        }
        user.setRole(newRole);
        user.setActive(request.active());
        return MemberResponse.from(userRepository.save(user));
    }

    @PostMapping("/{id}/reset-password")
    @Transactional
    public InvitationResponse restablecer(@RequestAttribute(AUTH) JwtService.JwtPayload auth, @PathVariable("id") UUID id) {
        Roles.requireAdmin(auth);
        User user = miembro(id, auth);
        if (user.getId().equals(auth.userId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Para tu propia cuenta usa «Cambiar contraseña».");
        }
        String temporal = TemporaryPasswords.generate();
        user.setPasswordHash(passwordService.hashPassword(temporal));
        user.setMustChangePassword(true);
        return new InvitationResponse(MemberResponse.from(userRepository.save(user)), temporal);
    }

    private User miembro(UUID id, JwtService.JwtPayload auth) {
        return userRepository.findByIdAndCompanyId(id, auth.companyId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    public record InviteRequest(
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 100) String name,
        @NotBlank(message = "El correo es obligatorio") @Email(message = "El correo no es válido") @Size(max = 150) String email,
        @Pattern(regexp = "(?i)ADMIN|COMERCIAL", message = "Rol no válido") String role
    ) {}

    public record UpdateMemberRequest(
        @Pattern(regexp = "(?i)ADMIN|COMERCIAL", message = "Rol no válido") String role,
        boolean active
    ) {}

    public record MemberResponse(UUID id, String name, String email, String role, boolean active,
                                 boolean mustChangePassword, Instant createdAt) {
        static MemberResponse from(User u) {
            return new MemberResponse(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.isActive(),
                u.isMustChangePassword(), u.getCreatedAt());
        }
    }

    /** La contraseña temporal solo viaja en esta respuesta; no se puede volver a consultar. */
    public record InvitationResponse(MemberResponse member, String temporaryPassword) {}
}
