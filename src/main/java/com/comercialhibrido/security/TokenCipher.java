package com.comercialhibrido.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifrado de secretos guardados en la base (tokens de WhatsApp de cada empresa) con
 * AES-256-GCM. La clave vive fuera de la base (AGENT_ENCRYPTION_KEY): una copia de la
 * base sola no expone los tokens. GCM además detecta cualquier alteración del valor.
 *
 * Formato: "enc:v1:" + base64(iv de 12 bytes + texto cifrado con su etiqueta).
 * Los valores sin prefijo se consideran heredados en texto plano y se leen tal cual.
 */
@Component
public class TokenCipher {

    public static final String PREFIX = "enc:v1:";

    private static final Logger log = LoggerFactory.getLogger(TokenCipher.class);
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(@Value("${agent.encryption-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            this.key = null;
            log.warn("AGENT_ENCRYPTION_KEY no está definida: los tokens de WhatsApp se guardan sin cifrar. "
                + "Genera una con: openssl rand -base64 32");
            return;
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("AGENT_ENCRYPTION_KEY debe estar en base64 (openssl rand -base64 32).");
        }
        if (raw.length != 32) {
            throw new IllegalStateException("AGENT_ENCRYPTION_KEY debe tener 32 bytes (256 bits); tiene " + raw.length + ".");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public boolean isEnabled() {
        return key != null;
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /** Cifra si hay clave; sin clave devuelve el valor tal cual (modo heredado). */
    public String encrypt(String plain) {
        if (plain == null || key == null || isEncrypted(plain)) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cifrar el secreto", e);
        }
    }

    /** Descifra valores con prefijo; los heredados en texto plano se devuelven tal cual. */
    public String decrypt(String stored) {
        if (stored == null || !isEncrypted(stored)) {
            return stored;
        }
        if (key == null) {
            throw new IllegalStateException("Hay tokens cifrados en la base pero AGENT_ENCRYPTION_KEY no está definida. "
                + "Configura la misma clave con la que se cifraron.");
        }
        try {
            byte[] data = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
            return new String(cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo descifrar el secreto: clave incorrecta o valor alterado.", e);
        }
    }
}
