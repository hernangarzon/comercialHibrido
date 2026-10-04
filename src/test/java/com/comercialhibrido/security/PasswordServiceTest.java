package com.comercialhibrido.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordServiceTest {

    private final PasswordService service = new PasswordService();

    @Test
    void hashBcryptValidaSoloLaContrasenaCorrecta() {
        String hash = service.hashPassword("secreta");

        assertThat(hash).startsWith("$2");
        assertThat(service.checkPassword("secreta", hash)).isTrue();
        assertThat(service.checkPassword("otra", hash)).isFalse();
        assertThat(service.needsUpgrade(hash)).isFalse();
    }

    @Test
    void aceptaHashSha256HeredadoYPideMigrarlo() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(("secreta" + "ComercialHibridoSalt2026*").getBytes(StandardCharsets.UTF_8));
        String legacy = HexFormat.of().formatHex(digest);

        assertThat(service.checkPassword("secreta", legacy)).isTrue();
        assertThat(service.checkPassword("otra", legacy)).isFalse();
        assertThat(service.needsUpgrade(legacy)).isTrue();
    }

    @Test
    void aceptaTextoPlanoHeredadoYPideMigrarlo() {
        assertThat(service.checkPassword("admin123", "admin123")).isTrue();
        assertThat(service.checkPassword("admin12", "admin123")).isFalse();
        assertThat(service.needsUpgrade("admin123")).isTrue();
    }

    @Test
    void rechazaValoresVacios() {
        assertThat(service.checkPassword(null, "x")).isFalse();
        assertThat(service.checkPassword("x", null)).isFalse();
        assertThat(service.checkPassword("x", "")).isFalse();
    }
}
