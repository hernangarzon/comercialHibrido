package com.comercialhibrido.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Permisos del panel. ADMIN configura el bot (conocimiento y catálogo);
 * COMERCIAL atiende conversaciones y consulta el dashboard.
 */
public final class Roles {

    public static final String ADMIN = "ADMIN";

    private Roles() {}

    public static void requireAdmin(JwtService.JwtPayload user) {
        if (user == null || !ADMIN.equalsIgnoreCase(user.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Esta acción requiere rol de administrador.");
        }
    }
}
