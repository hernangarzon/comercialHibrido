package com.comercialhibrido.repository;

import com.comercialhibrido.domain.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    /** El cliente de esa empresa con ese número (el mismo número en otra empresa es otro cliente). */
    Optional<Customer> findByCompanyIdAndPhoneNumber(UUID companyId, String phoneNumber);
}