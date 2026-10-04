package com.comercialhibrido.repository;

import com.comercialhibrido.domain.entity.Conversation;
import com.comercialhibrido.domain.enums.ConversationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /**
     * Busca la conversación más reciente del cliente con una empresa concreta.
     * Filtrar por empresa evita que un cliente que escribe a dos empresas
     * termine con sus mensajes mezclados en la conversación de la otra.
     */
    Optional<Conversation> findFirstByCustomerIdAndCompanyIdOrderByCreatedAtDesc(UUID customerId, UUID companyId);

    /**
     * Lista conversaciones de una empresa específica.
     */
    List<Conversation> findByCompanyIdOrderByUpdatedAtDesc(UUID companyId);

    /**
     * Lista conversaciones de una empresa específica filtradas por estado.
     */
    List<Conversation> findByCompanyIdAndStatusOrderByUpdatedAtDesc(UUID companyId, ConversationStatus status);

    /**
     * Comprueba que la conversación pertenezca a la empresa (aislamiento multiempresa).
     */
    boolean existsByIdAndCompanyId(UUID id, UUID companyId);

    long countByCompanyId(UUID companyId);

    /**
     * Empresa de una conversación, sin cargar la entidad (para enrutar eventos del panel).
     */
    @Query("SELECT c.company.id FROM Conversation c WHERE c.id = :id")
    Optional<UUID> findCompanyIdById(@Param("id") UUID id);
}