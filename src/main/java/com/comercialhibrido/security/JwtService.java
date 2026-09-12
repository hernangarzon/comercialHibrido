package com.comercialhibrido.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    @Value("${agent.jwt.secret:MiClaveSuperSecretaParaFirmarTokensJWTComercialHibrido2026*}")
    private String secretKey;

    private static final long EXPIRATION_HOURS = 24 * 7; // 7 días de validez
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String generarToken(UUID userId, UUID companyId, String email, String name, String role) {
        try {
            long now = Instant.now().getEpochSecond();
            long exp = now + (EXPIRATION_HOURS * 3600);

            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> payload = Map.of(
                "sub", userId.toString(),
                "companyId", companyId.toString(),
                "email", email,
                "name", name,
                "role", role,
                "iat", now,
                "exp", exp
            );

            String headerBase64 = encodeBase64Url(objectMapper.writeValueAsBytes(header));
            String payloadBase64 = encodeBase64Url(objectMapper.writeValueAsBytes(payload));

            String dataToSign = headerBase64 + "." + payloadBase64;
            String signature = hmacSha256(dataToSign, secretKey);

            return dataToSign + "." + signature;
        } catch (Exception e) {
            log.error("Error al generar JWT: {}", e.getMessage());
            throw new RuntimeException("Error generando token de autenticación", e);
        }
    }

    public JwtPayload validarYExtraer(String token) {
        try {
            if (token == null || !token.contains(".")) return null;
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;

            String dataToSign = parts[0] + "." + parts[1];
            String expectedSignature = hmacSha256(dataToSign, secretKey);

            if (!expectedSignature.equals(parts[2])) {
                log.warn("Firma de JWT inválida.");
                return null;
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> claims = objectMapper.readValue(payloadBytes, Map.class);

            long exp = ((Number) claims.get("exp")).longValue();
            if (Instant.now().getEpochSecond() > exp) {
                log.warn("JWT expirado.");
                return null;
            }

            return new JwtPayload(
                UUID.fromString((String) claims.get("sub")),
                UUID.fromString((String) claims.get("companyId")),
                (String) claims.get("email"),
                (String) claims.get("name"),
                (String) claims.get("role")
            );
        } catch (Exception e) {
            log.warn("Fallo al validar JWT: {}", e.getMessage());
            return null;
        }
    }

    private String encodeBase64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private String hmacSha256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        return encodeBase64Url(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    public record JwtPayload(UUID userId, UUID companyId, String email, String name, String role) {}
}