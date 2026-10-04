package com.comercialhibrido.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Hashing de contraseñas del panel con BCrypt.
 *
 * Acepta además dos formatos heredados para no dejar a nadie fuera:
 * SHA-256 con sal fija (versión anterior) y texto plano (usuarios cargados a mano).
 * AuthController los reemplaza por BCrypt en el primer login correcto
 * (ver {@link #needsUpgrade(String)}).
 */
@Service
public class PasswordService {

    private static final String LEGACY_SALT = "ComercialHibridoSalt2026*";
    private static final Pattern LEGACY_SHA256 = Pattern.compile("^[0-9a-fA-F]{64}$");

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public String hashPassword(String plainPassword) {
        return encoder.encode(plainPassword);
    }

    public boolean checkPassword(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        if (isBcrypt(storedHash)) {
            return encoder.matches(plainPassword, storedHash);
        }
        if (LEGACY_SHA256.matcher(storedHash).matches()
            && constantTimeEquals(legacySha256(plainPassword), storedHash.toLowerCase())) {
            return true;
        }
        return constantTimeEquals(plainPassword, storedHash);
    }

    /** true si el hash guardado no es BCrypt y debe re-hashearse tras un login válido. */
    public boolean needsUpgrade(String storedHash) {
        return !isBcrypt(storedHash);
    }

    private boolean isBcrypt(String storedHash) {
        return storedHash != null && storedHash.matches("^\\$2[aby]?\\$\\d{2}\\$.{53}$");
    }

    private String legacySha256(String plainPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((plainPassword + LEGACY_SALT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
