package com.comercialhibrido.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Solicitud de demo enviada desde la landing pública. */
@Entity
@Table(name = "sales_leads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesLead {

    public static final String NUEVA = "NUEVA";
    public static final String CONTACTADA = "CONTACTADA";
    public static final String DESCARTADA = "DESCARTADA";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 150)
    private String companyName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false, length = 40)
    private String phone;

    @Column(length = 2000)
    private String message;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = NUEVA;

    @Column(length = 64)
    private String sourceIp;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
