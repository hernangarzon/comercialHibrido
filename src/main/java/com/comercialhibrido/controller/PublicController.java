package com.comercialhibrido.controller;

import com.comercialhibrido.config.PlatformProperties;
import com.comercialhibrido.domain.entity.SalesLead;
import com.comercialhibrido.repository.SalesLeadRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Endpoints sin autenticación que usa la landing de ventas.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private static final Logger log = LoggerFactory.getLogger(PublicController.class);
    private static final int MAX_PER_WINDOW = 5;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final SalesLeadRepository salesLeadRepository;
    private final PlatformProperties platformProperties;

    /** Límite simple por IP en memoria contra envíos masivos del formulario. */
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    @GetMapping("/config")
    public Map<String, String> config() {
        return Map.of("salesWhatsapp", platformProperties.salesWhatsappDigits());
    }

    @PostMapping("/leads")
    public ResponseEntity<Map<String, String>> solicitarDemo(@Valid @RequestBody LeadRequest request, HttpServletRequest http) {
        // Campo trampa invisible para personas: si viene lleno es un bot. Se responde OK sin guardar.
        if (request.website() != null && !request.website().isBlank()) {
            return ResponseEntity.accepted().body(Map.of("status", "ok"));
        }
        String ip = http.getRemoteAddr();
        if (!permitir(ip)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Recibimos varias solicitudes desde tu conexión. Intenta de nuevo en un rato.");
        }
        SalesLead lead = salesLeadRepository.save(SalesLead.builder()
            .name(request.name().strip())
            .companyName(request.companyName().strip())
            .email(request.email().strip().toLowerCase())
            .phone(request.phone().strip())
            .message(request.message() == null || request.message().isBlank() ? null : request.message().strip())
            .sourceIp(ip)
            .build());
        log.info("Nueva solicitud de demo {} de {}", lead.getId(), lead.getCompanyName());
        return ResponseEntity.accepted().body(Map.of("status", "ok"));
    }

    private boolean permitir(String ip) {
        Instant now = Instant.now();
        Deque<Instant> window = attempts.computeIfAbsent(ip == null ? "?" : ip, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().isBefore(now.minus(WINDOW))) {
                window.pollFirst();
            }
            if (window.size() >= MAX_PER_WINDOW) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

    public record LeadRequest(
        @NotBlank(message = "Escribe tu nombre") @Size(max = 120) String name,
        @NotBlank(message = "Escribe el nombre de tu empresa") @Size(max = 150) String companyName,
        @NotBlank(message = "Escribe tu correo") @Email(message = "El correo no es válido") @Size(max = 150) String email,
        @NotBlank(message = "Escribe tu teléfono")
        @Pattern(regexp = "^[+0-9 ()-]{7,40}$", message = "El teléfono no es válido") String phone,
        @Size(max = 2000, message = "El mensaje admite hasta 2.000 caracteres") String message,
        // Autorización previa de tratamiento de datos (Ley 1581 de 2012).
        @AssertTrue(message = "Debes autorizar el tratamiento de tus datos para enviar la solicitud") boolean acceptedPrivacy,
        String website
    ) {}
}
