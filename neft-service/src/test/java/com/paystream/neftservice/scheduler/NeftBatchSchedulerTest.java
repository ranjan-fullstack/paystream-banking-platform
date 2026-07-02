package com.paystream.neftservice.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.neftservice.entity.NeftBatch;
import com.paystream.neftservice.entity.NeftTransaction;
import com.paystream.neftservice.entity.OutboxEvent;
import com.paystream.neftservice.enums.NeftBatchStatus;
import com.paystream.neftservice.enums.NeftStatus;
import com.paystream.neftservice.repository.NeftBatchRepository;
import com.paystream.neftservice.repository.NeftTransactionRepository;
import com.paystream.neftservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NeftBatchScheduler (batch processor) unit tests")
class NeftBatchSchedulerTest {

    @Mock
    private NeftTransactionRepository neftTransactionRepository;

    @Mock
    private NeftBatchRepository neftBatchRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private NeftBatchScheduler neftBatchScheduler;

    private NeftTransaction buildQueuedTxn(String reference, String beneficiary, BigDecimal amount) {
        NeftTransaction txn = new NeftTransaction();
        txn.setId(UUID.randomUUID());
        txn.setNeftReferenceNumber(reference);
        txn.setSenderAccountNumber("1111222233334444");
        txn.setBeneficiaryAccountNumber(beneficiary);
        txn.setBeneficiaryName("Beneficiary " + beneficiary);
        txn.setAmount(amount);
        txn.setStatus(NeftStatus.QUEUED);
        return txn;
    }

    @Test
    @DisplayName("Should set all queued NEFT transactions to BATCH_PROCESSING and write one outbox event each")
    void testProcessBatch_writesOutboxForAllQueuedTransactions() throws Exception {
        NeftTransaction txn1 = buildQueuedTxn("NEFT202606290001", "5555666677778888", new BigDecimal("10000"));
        NeftTransaction txn2 = buildQueuedTxn("NEFT202606290002", "9999888877776666", new BigDecimal("20000"));

        when(neftTransactionRepository.findByStatus(NeftStatus.QUEUED)).thenReturn(List.of(txn1, txn2));
        when(neftBatchRepository.save(any(NeftBatch.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        neftBatchScheduler.processNeftBatch();

        assertThat(txn1.getStatus()).isEqualTo(NeftStatus.BATCH_PROCESSING);
        assertThat(txn2.getStatus()).isEqualTo(NeftStatus.BATCH_PROCESSING);
        verify(outboxEventRepository, times(2)).save(any(OutboxEvent.class));

        ArgumentCaptor<NeftBatch> batchCaptor = ArgumentCaptor.forClass(NeftBatch.class);
        verify(neftBatchRepository, times(2)).save(batchCaptor.capture());
        NeftBatch finalBatch = batchCaptor.getValue();
        assertThat(finalBatch.getStatus()).isEqualTo(NeftBatchStatus.COMPLETED);
        assertThat(finalBatch.getSuccessCount()).isEqualTo(2);
        assertThat(finalBatch.getFailureCount()).isEqualTo(0);
        assertThat(finalBatch.getTotalAmount()).isEqualByComparingTo("30000");

        verify(neftTransactionRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("Should never call accountClient — settlement is now async via saga")
    void testProcessBatch_noDirectAccountClientCalls() throws Exception {
        NeftTransaction txn = buildQueuedTxn("NEFT202606290003", "5555666677778888", new BigDecimal("15000"));

        when(neftTransactionRepository.findByStatus(NeftStatus.QUEUED)).thenReturn(List.of(txn));
        when(neftBatchRepository.save(any(NeftBatch.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        neftBatchScheduler.processNeftBatch();

        assertThat(txn.getStatus()).isEqualTo(NeftStatus.BATCH_PROCESSING);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("Should do nothing when there are no queued NEFT transactions")
    void testProcessBatch_emptyQueue() {
        when(neftTransactionRepository.findByStatus(NeftStatus.QUEUED)).thenReturn(List.of());

        neftBatchScheduler.processNeftBatch();

        verify(neftBatchRepository, never()).save(any());
        verify(neftTransactionRepository, never()).saveAll(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should generate batch numbers in the BATCH-yyyyMMdd-HHmm format")
    void testBatchNumberGeneration() throws Exception {
        NeftTransaction txn = buildQueuedTxn("NEFT202606290005", "5555666677778888", new BigDecimal("5000"));
        when(neftTransactionRepository.findByStatus(NeftStatus.QUEUED)).thenReturn(List.of(txn));
        when(neftBatchRepository.save(any(NeftBatch.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        neftBatchScheduler.processNeftBatch();

        ArgumentCaptor<NeftBatch> batchCaptor = ArgumentCaptor.forClass(NeftBatch.class);
        verify(neftBatchRepository, atLeastOnce()).save(batchCaptor.capture());
        assertThat(batchCaptor.getAllValues().get(0).getBatchNumber())
                .matches("BATCH-\\d{8}-\\d{4}");
    }
}
