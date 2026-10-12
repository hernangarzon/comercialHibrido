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
@Table(
    name = "customers",
    uniqueConstraints = @UniqueConstraint(name = "customers_company_phone_key", columnNames = {"company_id", "phone_number"})
)
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
     * Empresa a la que le escribe. El mismo número que escribe a dos empresas son
     * dos clientes distintos: sus datos e historial no se mezclan.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /**
     * Numero de telefono en formato E.164 (ej: +573001234567), tal como
     * lo entrega el webhook de WhatsApp en el campo "wa_id". Unico dentro de cada empresa.
     */
    @Column(nullable = false)
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