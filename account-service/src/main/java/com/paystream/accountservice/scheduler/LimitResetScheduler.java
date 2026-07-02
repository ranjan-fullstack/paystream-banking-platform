package com.paystream.accountservice.scheduler;

import com.paystream.accountservice.repository.AccountLimitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Resets each account's per-payment-mode daily usage counter once its resetAt
 * timestamp has passed, so the configured dailyLimit applies per calendar day.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LimitResetScheduler {

    private final AccountLimitRepository accountLimitRepository;

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void resetExpiredDailyLimits() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextReset = now.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        int count = accountLimitRepository.resetExpiredLimits(now, nextReset);
        if (count > 0) {
            log.info("Reset daily usage counters for {} account limit(s)", count);
        }
    }
}
