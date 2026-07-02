package com.paystream.upiservice.scheduler;

import com.paystream.upiservice.entity.UpiCollectRequest;
import com.paystream.upiservice.entity.UpiTransaction;
import com.paystream.upiservice.enums.CollectRequestStatus;
import com.paystream.upiservice.enums.UpiTransactionStatus;
import com.paystream.upiservice.repository.UpiCollectRequestRepository;
import com.paystream.upiservice.repository.UpiTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CollectExpiryScheduler unit tests")
class CollectExpirySchedulerTest {

    @Mock
    private UpiTransactionRepository upiTransactionRepository;

    @Mock
    private UpiCollectRequestRepository upiCollectRequestRepository;

    @InjectMocks
    private CollectExpiryScheduler collectExpiryScheduler;

    @Test
    @DisplayName("Should mark stale pending collect requests and their transactions as expired")
    void testExpireStaleCollectRequests_marksExpired() {
        // Given
        UUID txnId = UUID.randomUUID();
        UpiTransaction txn = new UpiTransaction();
        txn.setId(txnId);
        txn.setUpiTransactionId("UPI1000000001");
        txn.setAmount(new BigDecimal("899"));
        txn.setStatus(UpiTransactionStatus.PENDING_PIN);
        txn.setExpiresAt(LocalDateTime.now().minusMinutes(5));

        UpiCollectRequest collectRequest = new UpiCollectRequest();
        collectRequest.setStatus(CollectRequestStatus.PENDING);

        when(upiTransactionRepository.findByStatusAndExpiresAtBefore(eq(UpiTransactionStatus.PENDING_PIN), any()))
                .thenReturn(List.of(txn));
        when(upiCollectRequestRepository.findByUpiTransactionId(txnId)).thenReturn(Optional.of(collectRequest));

        // When
        collectExpiryScheduler.expireStaleCollectRequests();

        // Then
        assertThat(txn.getStatus()).isEqualTo(UpiTransactionStatus.EXPIRED);
        assertThat(collectRequest.getStatus()).isEqualTo(CollectRequestStatus.EXPIRED);
        verify(upiCollectRequestRepository).save(collectRequest);
        verify(upiTransactionRepository).saveAll(List.of(txn));
    }

    @Test
    @DisplayName("Should not persist anything when there are no stale collect requests")
    void testExpireStaleCollectRequests_noneExpired_doesNotSave() {
        // Given
        when(upiTransactionRepository.findByStatusAndExpiresAtBefore(eq(UpiTransactionStatus.PENDING_PIN), any()))
                .thenReturn(List.of());

        // When
        collectExpiryScheduler.expireStaleCollectRequests();

        // Then
        verify(upiTransactionRepository, never()).saveAll(any());
        verify(upiCollectRequestRepository, never()).save(any());
    }
}
