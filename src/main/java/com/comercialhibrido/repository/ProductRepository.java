package com.comercialhibrido.repository;

import com.comercialhibrido.domain.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByCompanyIdAndAvailableTrueOrderByNameAsc(UUID companyId);
}