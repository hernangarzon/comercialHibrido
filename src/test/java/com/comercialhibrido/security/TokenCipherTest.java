package com.comercialhibrido.security;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenCipherTest {

    private static String nuevaClave() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }

    private final TokenCipher cipher = new TokenCipher(nuevaClave());

    @Test
    void cifraYDescifraSinDejarElTokenEnClaro() {
        String token = "EAAGtokenDeMetaMuyLargo1234567890";
        String guardado = cipher.encrypt(token);

        assertThat(guardado).startsWith(TokenCipher.PREFIX).doesNotContain(token);
        assertThat(cipher.decrypt(guardado)).isEqualTo(token);
        // IV aleatorio: el mismo token cifrado dos veces da valores distintos.
        assertThat(cipher.encrypt(token)).isNotEqualTo(guardado);
    }

    @Test
    void losTokensHeredadosEnTextoPlanoSeLeenTalCual() {
        assertThat(cipher.decrypt("EAAGtokenViejo")).isEqualTo("EAAGtokenViejo");
        assertThat(cipher.encrypt(null)).isNull();
    }

    @Test
    void detectaValoresAlteradosYClavesIncorrectas() {
        String guardado = cipher.encrypt("secreto");
        String alterado = guardado.substring(0, guardado.length() - 2) + (guardado.endsWith("A") ? "BB" : "AA");

        assertThatThrownBy(() -> cipher.decrypt(alterado)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TokenCipher(nuevaClave()).decrypt(guardado)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sinClaveNoCifraPeroNoPuedeLeerTokensCifrados() {
        TokenCipher sinClave = new TokenCipher("");
        assertThat(sinClave.isEnabled()).isFalse();
        assertThat(sinClave.encrypt("token")).isEqualTo("token");
        assertThatThrownBy(() -> sinClave.decrypt(cipher.encrypt("token"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rechazaClavesMalFormadas() {
        assertThatThrownBy(() -> new TokenCipher("no-es-base64!!")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TokenCipher(Base64.getEncoder().encodeToString(new byte[16])))
            .isInstanceOf(IllegalStateException.class);
    }
}
