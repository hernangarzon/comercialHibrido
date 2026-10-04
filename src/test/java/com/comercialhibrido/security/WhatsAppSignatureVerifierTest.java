package com.comercialhibrido.security;

import com.comercialhibrido.config.AgentProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppSignatureVerifierTest {

    private static final byte[] BODY = "{\"object\":\"whatsapp\",\"texto\":\"ñandú\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void aceptaFirmaCorrecta() throws Exception {
        WhatsAppSignatureVerifier verifier = verifierCon("secreto");
        assertThat(verifier.isValid(BODY, firmar(BODY, "secreto"))).isTrue();
    }

    @Test
    void rechazaFirmaDeOtroSecretoOCuerpoAlterado() throws Exception {
        WhatsAppSignatureVerifier verifier = verifierCon("secreto");
        assertThat(verifier.isValid(BODY, firmar(BODY, "otro"))).isFalse();

        byte[] alterado = "{\"object\":\"whatsapp\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.isValid(alterado, firmar(BODY, "secreto"))).isFalse();
    }

    @Test
    void rechazaHeaderAusenteOMalFormado() {
        WhatsAppSignatureVerifier verifier = verifierCon("secreto");
        assertThat(verifier.isValid(BODY, null)).isFalse();
        assertThat(verifier.isValid(BODY, "md5=abc")).isFalse();
    }

    @Test
    void sinSecretoConfiguradoRechazaTodo() throws Exception {
        WhatsAppSignatureVerifier verifier = verifierCon("");
        assertThat(verifier.isConfigured()).isFalse();
        assertThat(verifier.isValid(BODY, firmar(BODY, ""))).isFalse();
    }

    private static WhatsAppSignatureVerifier verifierCon(String secret) {
        return new WhatsAppSignatureVerifier(
            new AgentProperties(null, null, null, secret, null, null, null, null));
    }

    static String firmar(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.isEmpty() ? new byte[1] : secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }
}
