package com.comercialhibrido.security;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "secreto-de-pruebas-con-mas-de-32-caracteres";

    @Test
    void noArrancaSinSecretoOConSecretoCorto() {
        assertThatThrownBy(() -> new JwtService("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService("corto")).isInstanceOf(IllegalStateException.class);
        // El valor de ejemplo de .env.example es público: no debe aceptarse aunque sea largo.
        assertThatThrownBy(() -> new JwtService("genera-uno-con-openssl-rand-base64-48")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tokenGeneradoSeValidaYConservaLaEmpresa() {
        JwtService service = new JwtService(SECRET);
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        String token = service.generarToken(userId, companyId, "a@b.com", "Ana", "ADMIN");
        JwtService.JwtPayload payload = service.validarYExtraer(token);

        assertThat(payload).isNotNull();
        assertThat(payload.userId()).isEqualTo(userId);
        assertThat(payload.companyId()).isEqualTo(companyId);
    }

    @Test
    void rechazaTokenFirmadoConOtroSecretoOAlterado() {
        String token = new JwtService(SECRET)
            .generarToken(UUID.randomUUID(), UUID.randomUUID(), "a@b.com", "Ana", "ADMIN");

        assertThat(new JwtService(SECRET + "-distinto").validarYExtraer(token)).isNull();

        String[] partes = token.split("\\.");
        String alterado = partes[0] + "." + partes[1] + "x." + partes[2];
        assertThat(new JwtService(SECRET).validarYExtraer(alterado)).isNull();
    }
}
