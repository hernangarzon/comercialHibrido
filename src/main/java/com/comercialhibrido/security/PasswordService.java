package com.comercialhibrido.security;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class PasswordService {

    private static final String SALT = "ComercialHibridoSalt2026*";

    public String hashPassword(String plainPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String salted = plainPassword + SALT;
            byte[] hash = digest.digest(salted.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error al hashear contraseña", e);
        }
    }

    public boolean checkPassword(String plainPassword, String storedHash) {
        String hash = hashPassword(plainPassword);
        return hash.equalsIgnoreCase(storedHash);
    }
}