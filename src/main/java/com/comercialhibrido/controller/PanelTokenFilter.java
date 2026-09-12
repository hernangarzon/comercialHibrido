package com.comercialhibrido.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class PanelTokenFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Rutas públicas y descarga de imágenes/PDFs
        if (path.startsWith("/webhook") 
            || path.startsWith("/ws") 
            || path.startsWith("/api/auth") 
            || path.startsWith("/api/media") 
            || path.equals("/") 
            || path.endsWith(".html") 
            || path.endsWith(".js") 
            || path.endsWith(".css")) {
            
            filterChain.doFilter(request, response);
            return;
        }

        // Proteger el resto de endpoints de la API (conversaciones, mensajes manuales, etc.)
        if (path.startsWith("/api/")) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                JwtService.JwtPayload payload = jwtService.validarYExtraer(token);
                if (payload != null) {
                    request.setAttribute("authenticatedUser", payload);
                    filterChain.doFilter(request, response);
                    return;
                }
            }

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Acceso no autorizado. Inicia sesión.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}