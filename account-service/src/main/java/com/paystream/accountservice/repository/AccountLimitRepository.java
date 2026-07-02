package com.paystream.accountservice.repository;

import com.paystream.accountservice.entity.AccountLimit;
import com.paystream.accountservice.enums.PaymentMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountLimitRepository extends JpaRepository<AccountLimit, UUID> {
    List<AccountLimit> findByAccountId(UUID accountId);
    Optional<AccountLimit> findByAccountIdAndTransactionType(UUID accountId, PaymentMode transactionType);

    @Modifying
    @Query("UPDATE AccountLimit l SET l.usedTodayAmount = 0, l.resetAt = :nextReset WHERE l.resetAt <= :now")
    int resetExpiredLimits(@Param("now") LocalDateTime now, @Param("nextReset") LocalDateTime nextReset);
}
