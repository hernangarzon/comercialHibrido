package com.comercialhibrido.service;

import com.comercialhibrido.domain.entity.Conversation;
import com.comercialhibrido.exception.IllegalTransitionException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Lecturas de Conversation que necesitan traer la relacion 'customer' ya
 * cargada, para usarse fuera del contexto transaccional original (por
 * ejemplo, en EscalationNotificationListener, que corre AFTER_COMMIT,
 * cuando la sesion de Hibernate de la transaccion que escalo la
 * conversacion ya se cerro).
 *
 * Se separa de ConversationRepository a proposito: ese repositorio es de
 * uso general, y esta consulta con JOIN FETCH es especifica de este caso
 * de uso puntual (armar la notificacion del panel).
 */
@Component
interface ConversationRepositoryReader extends JpaRepository<Conversation, UUID> {

    @Query("SELECT c FROM Conversation c JOIN FETCH c.customer JOIN FETCH c.company WHERE c.id = :id")
    java.util.Optional<Conversation> buscarPorIdConCliente(UUID id);

    default Conversation buscarConCliente(UUID id) {
        return buscarPorIdConCliente(id)
            .orElseThrow(() -> new IllegalTransitionException(
                "Conversacion no encontrada al notificar escalacion: " + id));
    }
}