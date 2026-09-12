package com.comercialhibrido.repository;

import com.comercialhibrido.domain.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}