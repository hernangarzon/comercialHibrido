package com.comercialhibrido.security;

import com.comercialhibrido.domain.entity.User;
import com.comercialhibrido.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Autenticación del API del panel.
 *
 * Todo /api/** exige un JWT válido salvo el login y /api/public/** (landing).
 * Además del token se consulta el usuario en la base en cada petición: así una
 * cuenta desactivada o un cambio de rol se aplican de inmediato, sin esperar a
 * que venza el token. Con contraseña temporal solo se permite /api/account/**.
 */
@Component
@RequiredArgsConstructor
public class PanelTokenFilter extends OncePerRequestFilter {

    public static final String AUTH_ATTRIBUTE = "authenticatedUser";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();
        boolean protegido = path.startsWith("/api/")
            && !path.startsWith("/api/auth/")
            && !path.startsWith("/api/public/");
        if (!protegido) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        JwtService.JwtPayload token = authHeader != null && authHeader.startsWith("Bearer ")
            ? jwtService.validarYExtraer(authHeader.substring(7))
            : null;
        User user = token == null ? null : userRepository.findById(token.userId())
            .filter(User::isActive)
            .filter(u -> u.getCompany().getId().equals(token.companyId()))
            .orElse(null);

        if (user == null) {
            error(response, HttpServletResponse.SC_UNAUTHORIZED, "Acceso no autorizado. Inicia sesión.");
            return;
        }
        if (user.isMustChangePassword() && !path.startsWith("/api/account/")) {
            error(response, HttpServletResponse.SC_FORBIDDEN, "Debes cambiar tu contraseña temporal antes de continuar.");
            return;
        }

        // Datos frescos de la base (rol y nombre pueden haber cambiado desde el login).
        request.setAttribute(AUTH_ATTRIBUTE, new JwtService.JwtPayload(
            user.getId(), user.getCompany().getId(), user.getEmail(), user.getName(), user.getRole()));
        filterChain.doFilter(request, response);
    }

    private static void error(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }
}
