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

    public boolean isValid(String rawBody, String signatureHeader) {
        String secret = agentProperties.whatsappAppSecret();
        if (secret == null || secret.isBlank()) {
            return true;
        }
        if (rawBody == null || signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
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
