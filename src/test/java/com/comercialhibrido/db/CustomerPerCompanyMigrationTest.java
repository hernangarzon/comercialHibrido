package com.comercialhibrido.db;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V7 sobre datos como los de producción: un cliente que habló con dos empresas se
 * separa en dos (sin perder conversaciones), uno normal queda igual y uno sin
 * conversaciones se elimina. Después el número es único por empresa.
 */
class CustomerPerCompanyMigrationTest {

    private static EmbeddedPostgres postgres;
    private static JdbcTemplate jdbc;
    private static DataSource dataSource;

    private static final UUID EMPRESA_A = UUID.randomUUID();
    private static final UUID EMPRESA_B = UUID.randomUUID();
    private static final UUID COMPARTIDO = UUID.randomUUID();
    private static final UUID NORMAL = UUID.randomUUID();
    private static final UUID HUERFANO = UUID.randomUUID();
    private static final UUID CONV_COMPARTIDO_A = UUID.randomUUID();
    private static final UUID CONV_COMPARTIDO_B = UUID.randomUUID();
    private static final UUID CONV_NORMAL = UUID.randomUUID();

    @BeforeAll
    static void migrarConDatos() throws IOException {
        postgres = EmbeddedPostgres.start();
        dataSource = postgres.getPostgresDatabase();
        jdbc = new JdbcTemplate(dataSource);

        flyway("6").migrate();

        jdbc.update("INSERT INTO companies (id, name, whatsapp_phone_number_id) VALUES (?, 'A', '111'), (?, 'B', '222')", EMPRESA_A, EMPRESA_B);
        jdbc.update("INSERT INTO customers (id, phone_number, display_name, created_at) VALUES (?, '573001', 'Hernán', now() - interval '2 days')", COMPARTIDO);
        jdbc.update("INSERT INTO customers (id, phone_number, display_name) VALUES (?, '573002', 'Laura')", NORMAL);
        jdbc.update("INSERT INTO customers (id, phone_number, display_name) VALUES (?, '573003', 'Sin conversaciones')", HUERFANO);
        // El cliente compartido habló primero con A y luego con B.
        conversacion(CONV_COMPARTIDO_A, EMPRESA_A, COMPARTIDO, "2 days");
        conversacion(CONV_COMPARTIDO_B, EMPRESA_B, COMPARTIDO, "1 day");
        conversacion(CONV_NORMAL, EMPRESA_A, NORMAL, "3 hours");
        jdbc.update("INSERT INTO messages (conversation_id, sender, content) VALUES (?, 'CLIENTE', 'Hola A'), (?, 'CLIENTE', 'Hola B')",
            CONV_COMPARTIDO_A, CONV_COMPARTIDO_B);

        flyway(null).migrate();
    }

    @AfterAll
    static void stop() throws IOException {
        postgres.close();
    }

    @Test
    void elClienteCompartidoSeSeparaEnUnoPorEmpresaSinPerderConversaciones() {
        UUID clienteA = jdbc.queryForObject("SELECT customer_id FROM conversations WHERE id = ?", UUID.class, CONV_COMPARTIDO_A);
        UUID clienteB = jdbc.queryForObject("SELECT customer_id FROM conversations WHERE id = ?", UUID.class, CONV_COMPARTIDO_B);

        assertThat(clienteA).isEqualTo(COMPARTIDO); // la empresa más antigua conserva el registro original
        assertThat(clienteB).isNotEqualTo(COMPARTIDO);

        Map<String, Object> copia = jdbc.queryForMap("SELECT phone_number, display_name, company_id FROM customers WHERE id = ?", clienteB);
        assertThat(copia.get("phone_number")).isEqualTo("573001");
        assertThat(copia.get("display_name")).isEqualTo("Hernán");
        assertThat(copia.get("company_id")).isEqualTo(EMPRESA_B);
        assertThat(jdbc.queryForObject("SELECT company_id FROM customers WHERE id = ?", UUID.class, COMPARTIDO)).isEqualTo(EMPRESA_A);

        // Los mensajes siguen en sus conversaciones.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM messages", Integer.class)).isEqualTo(2);
    }

    @Test
    void elClienteNormalQuedaIgualYElHuerfanoSeElimina() {
        assertThat(jdbc.queryForObject("SELECT company_id FROM customers WHERE id = ?", UUID.class, NORMAL)).isEqualTo(EMPRESA_A);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers WHERE id = ?", Integer.class, HUERFANO)).isZero();
        List<UUID> sinEmpresa = jdbc.queryForList("SELECT id FROM customers WHERE company_id IS NULL", UUID.class);
        assertThat(sinEmpresa).isEmpty();
    }

    @Test
    void despuesElNumeroEsUnicoPorEmpresaYNoGlobal() {
        // El mismo número en otra empresa se permite...
        jdbc.update("INSERT INTO customers (phone_number, company_id) VALUES ('573002', ?)", EMPRESA_B);
        // ...pero no repetido dentro de la misma empresa.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO customers (phone_number, company_id) VALUES ('573002', ?)", EMPRESA_A))
            .hasMessageContaining("customers_company_phone_key");
    }

    private static void conversacion(UUID id, UUID company, UUID customer, String haceCuanto) {
        jdbc.update("INSERT INTO conversations (id, company_id, customer_id, created_at) VALUES (?, ?, ?, now() - CAST(? AS interval))",
            id, company, customer, haceCuanto);
    }

    private static Flyway flyway(String target) {
        var config = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").cleanDisabled(true);
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }
}
