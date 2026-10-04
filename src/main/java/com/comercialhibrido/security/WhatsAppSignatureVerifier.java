package com.comercialhibrido.security;

import com.comercialhibrido.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@RequiredArgsConstructor
public class WhatsAppSignatureVerifier {

    private final AgentProperties agentProperties;

    public boolean isConfigured() {
        String secret = agentProperties.whatsappAppSecret();
        return secret != null && !secret.isBlank();
    }

    /**
     * Valida X-Hub-Signature-256 sobre los bytes exactos recibidos. Sin App Secret
     * configurado rechaza todo: aceptar sin verificar permitiria inyectar mensajes falsos.
     */
    public boolean isValid(byte[] rawBody, String signatureHeader) {
        if (!isConfigured()) {
            return false;
        }
        if (rawBody == null || signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                agentProperties.whatsappAppSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(rawBody);
            String expected = "sha256=" + toHex(digest);
            return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                signatureHeader.getBytes(StandardCharsets.US_ASCII)
            );
        } catch (Exception e) {
            return false;
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }
}
