package com.comercialhibrido.domain.entity;

import com.comercialhibrido.domain.enums.ConversationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConversationStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /**
     * Identificador del comercial (humano) actualmente asignado o que tomo
     * control. Nulo mientras la conversacion esta en BOT_ACTIVO sin asignar.
     * Se referencia por id de usuario del panel, la entidad Usuario/Comercial
     * se agrega cuando construyamos el panel Angular y su autenticacion.
     */
    private UUID assignedSalespersonId;

    /**
     * Resumen generado por LLM de los mensajes anteriores a lastSummarizedAt.
     * Reemplaza el historial crudo viejo en el contexto que se le manda al
     * modelo, evitando reenviar la conversacion completa en cada turno.
     */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(columnDefinition = "TEXT")
    private String summary;

    /** Marca de tiempo del mensaje mas reciente cubierto por 'summary'. */
    private Instant lastSummarizedAt;

    /** Momento de la última escalación a un asesor (métricas del dashboard). */
    private Instant escalatedAt;

    /**
     * Puntaje de intencion de compra (0-100) devuelto por el LLM de forma
     * estructurada en cada respuesta. Se usa para decidir si se dispara
     * la notificacion de "lead caliente" al comercial.
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer leadScore = 0;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * Bloqueo optimista: evita que dos escrituras concurrentes sobre el
     * estado de la misma conversacion (ej: el webhook procesando un mensaje
     * del cliente justo cuando el comercial presiona "tomar control") se
     * pisen entre si sin que el sistema se entere.
     */
    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        // Respeta fechas ya asignadas (importaciones o datos de demostración).
        Instant now = Instant.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
        if (this.status == null) {
            this.status = ConversationStatus.BOT_ACTIVO;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}