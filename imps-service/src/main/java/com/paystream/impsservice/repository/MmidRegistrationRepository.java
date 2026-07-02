package com.paystream.impsservice.repository;

import com.paystream.impsservice.entity.MmidRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MmidRegistrationRepository extends JpaRepository<MmidRegistration, UUID> {
    Optional<MmidRegistration> findByMobileNumber(String mobileNumber);
    boolean existsByMmid(String mmid);
}
