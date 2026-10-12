package com.comercialhibrido.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Estado real del servicio para monitores externos (UptimeRobot, Railway): responde 503
 * si la base no contesta o si el worker dejó de procesar mensajes, para que llegue una
 * alerta antes de que los clientes lo noten. Solo expone conteos, nunca datos.
 */
@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);
    /** Mensajes vencidos hace más de esto sin procesar = el worker está detenido. */
    private static final int STUCK_MINUTES = 10;

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "comercial-hibrido");
        body.put("timestamp", Instant.now().toString());

        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            body.put("database", "UP");
        } catch (Exception e) {
            log.error("Health: la base de datos no responde: {}", e.getMessage());
            body.put("database", "DOWN");
            body.put("status", "DOWN");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }

        // Fechas calculadas aquí (SQL portable: PostgreSQL en producción, H2 en dev/tests).
        Timestamp atascadoAntes = Timestamp.from(Instant.now().minus(Duration.ofMinutes(STUCK_MINUTES)));
        Timestamp ultimas24h = Timestamp.from(Instant.now().minus(Duration.ofHours(24)));
        int atascados = count("SELECT count(*) FROM inbound_message_jobs WHERE status = 'PENDING' AND next_attempt_at < ?", atascadoAntes)
            + count("SELECT count(*) FROM outbound_message_jobs WHERE status = 'PENDING' AND next_attempt_at < ?", atascadoAntes);
        int fallidos24h = count("SELECT count(*) FROM inbound_message_jobs WHERE status = 'FAILED' AND updated_at > ?", ultimas24h)
            + count("SELECT count(*) FROM outbound_message_jobs WHERE status = 'FAILED' AND updated_at > ?", ultimas24h);
        body.put("stuckJobs", atascados);
        body.put("failedJobsLast24h", fallidos24h);

        if (atascados > 0) {
            log.error("Health: {} mensaje(s) llevan más de {} min sin procesarse; el worker podría estar detenido.", atascados, STUCK_MINUTES);
            body.put("status", "DEGRADED");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
        body.put("status", "UP");
        return ResponseEntity.ok(body);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
