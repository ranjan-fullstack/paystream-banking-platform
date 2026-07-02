package com.paystream.upiservice.repository;

import com.paystream.upiservice.entity.VirtualPaymentAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VpaRepository extends JpaRepository<VirtualPaymentAddress, UUID> {
    Optional<VirtualPaymentAddress> findByVpa(String vpa);
    List<VirtualPaymentAddress> findByCustomerId(String customerId);
    boolean existsByVpa(String vpa);
}
