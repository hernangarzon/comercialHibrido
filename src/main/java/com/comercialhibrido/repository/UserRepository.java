package com.comercialhibrido.repository;

import com.comercialhibrido.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailAndActiveTrue(String email);

    /** Usuario con su empresa cargada (el filtro de seguridad corre fuera de una sesión JPA). */
    @Query("SELECT u FROM User u JOIN FETCH u.company WHERE u.id = :id")
    Optional<User> findWithCompanyById(@Param("id") UUID id);

    long countByCompanyId(UUID companyId);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByCompanyIdOrderByNameAsc(UUID companyId);

    Optional<User> findByIdAndCompanyId(UUID id, UUID companyId);

    long countByCompanyIdAndRoleIgnoreCaseAndActiveTrue(UUID companyId, String role);

    long countByCompanyIdAndActiveTrue(UUID companyId);
}
