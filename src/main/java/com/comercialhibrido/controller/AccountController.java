package com.comercialhibrido.controller;

import com.comercialhibrido.domain.entity.User;
import com.comercialhibrido.repository.UserRepository;
import com.comercialhibrido.security.JwtService;
import com.comercialhibrido.security.PanelTokenFilter;
import com.comercialhibrido.security.PasswordService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Cuenta del usuario autenticado. Disponible aunque tenga una contraseña temporal. */
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final UserRepository userRepository;
    private final PasswordService passwordService;

    @PutMapping("/password")
    public ResponseEntity<Void> cambiarContrasena(
        @RequestAttribute(PanelTokenFilter.AUTH_ATTRIBUTE) JwtService.JwtPayload auth,
        @Valid @RequestBody ChangePasswordRequest request
    ) {
        User user = userRepository.findById(auth.userId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida"));
        if (!passwordService.checkPassword(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta.");
        }
        if (request.newPassword().equals(request.currentPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña debe ser distinta de la actual.");
        }
        user.setPasswordHash(passwordService.hashPassword(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }

    public record ChangePasswordRequest(
        @NotBlank(message = "Escribe tu contraseña actual") String currentPassword,
        @NotBlank @Size(min = 8, max = 100, message = "La nueva contraseña debe tener entre 8 y 100 caracteres") String newPassword
    ) {}
}
