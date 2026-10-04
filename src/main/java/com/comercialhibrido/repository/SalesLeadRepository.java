package com.comercialhibrido.repository;

import com.comercialhibrido.domain.entity.SalesLead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SalesLeadRepository extends JpaRepository<SalesLead, UUID> {

    List<SalesLead> findTop200ByOrderByCreatedAtDesc();

    long countByStatus(String status);
}
