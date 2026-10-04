package com.comercialhibrido.repository;

import com.comercialhibrido.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailAndActiveTrue(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByCompanyIdOrderByNameAsc(UUID companyId);

    Optional<User> findByIdAndCompanyId(UUID id, UUID companyId);

    long countByCompanyIdAndRoleIgnoreCaseAndActiveTrue(UUID companyId, String role);

    long countByCompanyIdAndActiveTrue(UUID companyId);
}
