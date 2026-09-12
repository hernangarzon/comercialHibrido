package com.comercialhibrido.domain.entity;

import com.comercialhibrido.domain.enums.DeliveryStatus;
import com.comercialhibrido.domain.enums.MessageSender;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "messages",
    uniqueConstraints = @UniqueConstraint(columnNames = "whatsapp_message_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageSender sender;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "whatsapp_message_id", unique = true)
    private String whatsappMessageId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    @Column(length = 2000)
    private String deliveryError;

    @Column(length = 30)
    @Builder.Default
    private String mediaType = "TEXT"; // 'TEXT', 'IMAGE', 'DOCUMENT'

    @Column(length = 255)
    private String mediaId;

    @Column(length = 255)
    private String mediaFilename;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        if (this.deliveryStatus == null) {
            this.deliveryStatus = DeliveryStatus.PENDING;
        }
        if (this.mediaType == null) {
            this.mediaType = "TEXT";
        }
    }
}