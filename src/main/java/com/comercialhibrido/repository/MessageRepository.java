package com.comercialhibrido.repository;

import com.comercialhibrido.domain.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    boolean existsByWhatsappMessageId(String whatsappMessageId);

    Optional<Message> findByWhatsappMessageId(String whatsappMessageId);

    /**
     * Mensajes más recientes para armar el contexto del bot (orden descendente).
     */
    List<Message> findTop15ByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    /**
     * Historial completo ordenado cronológicamente para el chat del panel web.
     */
    List<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    /**
     * Obtiene el último mensaje para mostrar la vista previa en la lista de chats.
     */
    Optional<Message> findFirstByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    /**
     * Verifica que un archivo multimedia pertenezca a una conversación de la empresa.
     */
    boolean existsByMediaIdAndConversation_Company_Id(String mediaId, UUID companyId);

    /** Último mensaje del cliente en una conversación: abre la ventana de 24 h de WhatsApp. */
    @Query("""
        SELECT MAX(m.createdAt) FROM Message m
         WHERE m.conversation.id = :conversationId
           AND m.sender = com.comercialhibrido.domain.enums.MessageSender.CLIENTE
        """)
    Instant lastClientMessageAtInConversation(@Param("conversationId") UUID conversationId);

    /** Último mensaje del cliente en cada conversación de la empresa (una sola consulta para la bandeja). */
    @Query("""
        SELECT new com.comercialhibrido.repository.ConversationInstant(m.conversation.id, MAX(m.createdAt))
          FROM Message m
         WHERE m.conversation.company.id = :companyId
           AND m.sender = com.comercialhibrido.domain.enums.MessageSender.CLIENTE
         GROUP BY m.conversation.id
        """)
    List<ConversationInstant> lastClientMessageByConversation(@Param("companyId") UUID companyId);

    /** Último mensaje de un cliente a la empresa (null si nunca llegó ninguno). */
    @Query("""
        SELECT MAX(m.createdAt) FROM Message m
         WHERE m.conversation.company.id = :companyId
           AND m.sender = com.comercialhibrido.domain.enums.MessageSender.CLIENTE
        """)
    Instant lastClientMessageAt(@Param("companyId") UUID companyId);

    /**
     * Mensajes de la empresa en un rango, solo los campos que necesita el dashboard.
     */
    @Query("""
        SELECT new com.comercialhibrido.repository.MessageStat(m.conversation.id, m.sender, m.createdAt)
          FROM Message m
         WHERE m.conversation.company.id = :companyId
           AND m.createdAt >= :from AND m.createdAt < :to
        """)
    List<MessageStat> statsByCompany(@Param("companyId") UUID companyId, @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Para cada conversación escalada en el rango, la primera respuesta de un asesor
     * posterior a la escalación. Las escalaciones sin respuesta no aparecen.
     */
    @Query("""
        SELECT new com.comercialhibrido.repository.ResponseSample(c.id, c.escalatedAt, MIN(m.createdAt))
          FROM Message m JOIN m.conversation c
         WHERE c.company.id = :companyId
           AND c.escalatedAt >= :from AND c.escalatedAt < :to
           AND m.sender = com.comercialhibrido.domain.enums.MessageSender.COMERCIAL
           AND m.createdAt >= c.escalatedAt
         GROUP BY c.id, c.escalatedAt
        """)
    List<ResponseSample> firstAgentReplyAfterEscalation(
        @Param("companyId") UUID companyId, @Param("from") Instant from, @Param("to") Instant to);
}