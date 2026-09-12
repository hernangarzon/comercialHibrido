package com.comercialhibrido.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue
    private UUID id;

    /**
     * Numero de telefono en formato E.164 (ej: +573001234567), tal como
     * lo entrega el webhook de WhatsApp en el campo "wa_id". Es el
     * identificador natural del cliente y debe ser unico.
     */
    @Column(nullable = false, unique = true)
    private String phoneNumber;

    /** Nombre de perfil de WhatsApp, informativo. El nombre real se captura al CRM cuando aplica. */
    private String displayName;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}