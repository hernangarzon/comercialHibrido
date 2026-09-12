package com.comercialhibrido.repository;

import com.comercialhibrido.domain.entity.Conversation;
import com.comercialhibrido.domain.enums.ConversationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /**
     * Busca la conversación activa del cliente.
     */
    Optional<Conversation> findFirstByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    /**
     * Lista todas las conversaciones filtradas por estado (ordenadas por última actividad).
     */
    List<Conversation> findByStatusOrderByUpdatedAtDesc(ConversationStatus status);

    /**
     * Lista conversaciones de una empresa específica.
     */
    List<Conversation> findByCompanyIdOrderByUpdatedAtDesc(UUID companyId);

    /**
     * Lista conversaciones de una empresa específica filtradas por estado.
     */
    List<Conversation> findByCompanyIdAndStatusOrderByUpdatedAtDesc(UUID companyId, ConversationStatus status);
}