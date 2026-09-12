package com.comercialhibrido.controller;

import com.comercialhibrido.domain.entity.User;
import com.comercialhibrido.repository.UserRepository;
import com.comercialhibrido.security.JwtService;
import com.comercialhibrido.security.PasswordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordService passwordService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        String email = request.email() != null ? request.email().trim().toLowerCase() : "";
        String pass = request.password() != null ? request.password().trim() : "";

        User user = userRepository.findByEmailAndActiveTrue(email)
            .orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Usuario no encontrado en la base de datos."));
        }

        boolean passValida = pass.equals(user.getPasswordHash()) 
            || passwordService.checkPassword(pass, user.getPasswordHash());

        if (!passValida) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Contraseña incorrecta."));
        }

        String token = jwtService.generarToken(
            user.getId(),
            user.getCompany().getId(),
            user.getEmail(),
            user.getName(),
            user.getRole()
        );

        return ResponseEntity.ok(new LoginResponse(
            token,
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getRole(),
            user.getCompany().getId(),
            user.getCompany().getName()
        ));
    }

    public record LoginRequest(String email, String password) {}

    public record LoginResponse(
        String token,
        UUID userId,
        String name,
        String email,
        String role,
        UUID companyId,
        String companyName
    ) {}
}