package com.paystream.accountservice.scheduler;

import com.paystream.accountservice.repository.AccountLimitRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LimitResetScheduler unit tests")
class LimitResetSchedulerTest {

    @Mock
    private AccountLimitRepository accountLimitRepository;

    @InjectMocks
    private LimitResetScheduler limitResetScheduler;

    @Test
    @DisplayName("Should issue a single bulk UPDATE to reset expired daily limits")
    void testResetDailyLimits_issuesBulkUpdate() {
        when(accountLimitRepository.resetExpiredLimits(any(), any())).thenReturn(3);

        limitResetScheduler.resetExpiredDailyLimits();

        verify(accountLimitRepository).resetExpiredLimits(any(), any());
    }

    @Test
    @DisplayName("Should not log when no limits were reset")
    void testResetDailyLimits_noneExpired_noLog() {
        when(accountLimitRepository.resetExpiredLimits(any(), any())).thenReturn(0);

        limitResetScheduler.resetExpiredDailyLimits();

        verify(accountLimitRepository).resetExpiredLimits(any(), any());
    }
}
