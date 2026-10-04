package com.comercialhibrido.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Errores del API con un mensaje legible para el panel: {"error": "..."}.
 * Sin esto Spring devuelve el cuerpo genérico y oculta el motivo.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException e) {
        String reason = e.getReason() != null ? e.getReason() : e.getStatusCode().toString();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", reason));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            // Los mensajes propios empiezan en mayúscula; los genéricos del validador se prefijan con el campo.
            .map(f -> f.getDefaultMessage() != null && !f.getDefaultMessage().isEmpty()
                    && Character.isUpperCase(f.getDefaultMessage().charAt(0))
                ? f.getDefaultMessage()
                : f.getField() + ": " + f.getDefaultMessage())
            .orElse("Datos inválidos");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
