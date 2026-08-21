package com.paystream.accountservice.repository;

import com.paystream.accountservice.entity.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {
    Optional<BankAccount> findByAccountNumber(String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BankAccount> findWithLockByAccountNumber(String accountNumber);

    List<BankAccount> findByCustomerId(String customerId);

    List<BankAccount> findByUserId(Long userId);

    List<BankAccount> findByBranchCode(String branchCode);

    boolean existsByAccountNumber(String accountNumber);
}
