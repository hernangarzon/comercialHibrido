package com.comercialhibrido.security;

import java.security.SecureRandom;

/** Contraseñas temporales legibles (sin 0/O ni 1/l/I) para invitaciones y restablecimientos. */
public final class TemporaryPasswords {

    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private TemporaryPasswords() {}

    public static String generate() {
        StringBuilder sb = new StringBuilder(14);
        for (int i = 0; i < 12; i++) {
            if (i == 4 || i == 8) sb.append('-');
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
