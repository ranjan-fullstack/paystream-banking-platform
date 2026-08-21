package com.paystream.accountservice.repository;

import com.paystream.accountservice.entity.AccountPaymentConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountPaymentConfigRepository extends JpaRepository<AccountPaymentConfig, UUID> {
    Optional<AccountPaymentConfig> findByAccountId(UUID accountId);
}
