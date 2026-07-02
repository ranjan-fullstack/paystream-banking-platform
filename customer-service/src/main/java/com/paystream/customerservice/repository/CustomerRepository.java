package com.paystream.customerservice.repository;

import com.paystream.customerservice.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByCustomerId(String customerId);
    boolean existsByEmail(String email);
    boolean existsByMobile(String mobile);
    boolean existsByCustomerId(String customerId);
}
