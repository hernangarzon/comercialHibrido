package com.comercialhibrido.db;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aplica las migraciones sobre un PostgreSQL real (embebido) desde cero y arranca
 * la app con ddl-auto=validate: si una entidad no coincide con el esquema, falla.
 */
@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTest {

    private static final EmbeddedPostgres POSTGRES = start();

    private static EmbeddedPostgres start() {
        try {
            return EmbeddedPostgres.start();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo iniciar PostgreSQL embebido", e);
        }
    }

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @AfterAll
    static void stop() throws IOException {
        POSTGRES.close();
    }

    @Autowired
    JdbcTemplate jdbc;

    private static int migracionesEnElProyecto() {
        try {
            return new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/V*.sql").length;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void migracionesCreanUnEsquemaQueHibernateValida() {
        Integer aplicadas = jdbc.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);
        assertThat(aplicadas).isEqualTo(migracionesEnElProyecto());

        Integer escaladoEn = jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_name = 'conversations' AND column_name = 'escalated_at'",
            Integer.class);
        assertThat(escaladoEn).isEqualTo(1);

        Integer tablas = jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'",
            Integer.class);
        assertThat(tablas).isEqualTo(9);
    }
}
