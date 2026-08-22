package com.paystream.neftservice.service.impl;

import com.paystream.neftservice.client.AccountClient;
import com.paystream.neftservice.client.dto.AccountValidationResponse;
import com.paystream.neftservice.client.dto.PaymentRailConfigResponse;
import com.paystream.neftservice.dto.NeftBatchResponse;
import com.paystream.neftservice.dto.NeftTransactionResponse;
import com.paystream.neftservice.dto.NeftTransferRequest;
import com.paystream.neftservice.entity.NeftBatch;
import com.paystream.neftservice.entity.NeftTransaction;
import com.paystream.neftservice.enums.NeftStatus;
import com.paystream.neftservice.exception.DailyLimitExceededException;
import com.paystream.neftservice.exception.InvalidAccountException;
import com.paystream.neftservice.exception.NeftTransactionNotFoundException;
import com.paystream.neftservice.exception.NeftWindowClosedException;
import com.paystream.neftservice.exception.PaymentRailNotEnabledException;
import com.paystream.neftservice.exception.PerTransactionLimitExceededException;
import com.paystream.neftservice.exception.ServiceUnavailableException;
import com.paystream.neftservice.repository.NeftBatchRepository;
import com.paystream.neftservice.repository.NeftTransactionRepository;
import com.paystream.neftservice.service.NeftService;
import com.paystream.neftservice.util.NeftWindowValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NeftServiceImpl implements NeftService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final NeftTransactionRepository neftTransactionRepository;
    private final NeftBatchRepository neftBatchRepository;
    private final AccountClient accountClient;
    private final NeftWindowValidator windowValidator;

    @Override
    @Transactional
    public NeftTransactionResponse initiateTransfer(NeftTransferRequest request) {
        validatePaymentRail(request.getSenderAccountNumber(), request.getAmount());

        if (!windowValidator.isWithinWindow(LocalDateTime.now())) {
            throw new NeftWindowClosedException();
        }

        AccountValidationResponse senderValidation = accountClient.validate(request.getSenderAccountNumber());
        if (!senderValidation.isValid()) {
            throw new InvalidAccountException("Sender account is not valid: " + senderValidation.getReason());
        }

        NeftTransaction txn = new NeftTransaction();
        txn.setNeftReferenceNumber(generateUniqueReference());
        txn.setCustomerId(request.getCustomerId());
        txn.setSenderAccountNumber(request.getSenderAccountNumber());
        txn.setSenderIfsc(request.getSenderIfsc());
        txn.setBeneficiaryAccountNumber(request.getBeneficiaryAccountNumber());
        txn.setBeneficiaryIfsc(request.getBeneficiaryIfsc());
        txn.setBeneficiaryName(request.getBeneficiaryName());
        txn.setAmount(request.getAmount());
        txn.setRemarks(request.getRemarks());
        txn.setStatus(NeftStatus.QUEUED);

        txn = neftTransactionRepository.save(txn);
        return toResponse(txn);
    }

    @Override
    public NeftTransactionResponse trackStatus(String referenceNumber) {
        return toResponse(findOrThrow(referenceNumber));
    }

    @Override
    public List<NeftTransactionResponse> getHistory(String customerId) {
        return neftTransactionRepository.findByCustomerIdOrderByInitiatedAtDesc(customerId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<NeftBatchResponse> getBatches() {
        return neftBatchRepository.findAll().stream().map(this::toBatchResponse).collect(Collectors.toList());
    }

    @Override
    public NeftBatchResponse getBatchDetails(String batchNumber) {
        return neftBatchRepository.findByBatchNumber(batchNumber)
                .map(this::toBatchResponse)
                .orElseThrow(() -> new NeftTransactionNotFoundException(batchNumber));
    }

    private NeftTransaction findOrThrow(String referenceNumber) {
        return neftTransactionRepository.findByNeftReferenceNumber(referenceNumber)
                .orElseThrow(() -> new NeftTransactionNotFoundException(referenceNumber));
    }

    private String generateUniqueReference() {
        String datePart = LocalDateTime.now().format(DATE_FMT);
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "NEFT" + datePart + String.format("%04d", RANDOM.nextInt(10000));
            if (!neftTransactionRepository.existsByNeftReferenceNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique NEFT reference number, please retry");
    }

    private NeftTransactionResponse toResponse(NeftTransaction txn) {
        return NeftTransactionResponse.builder()
                .neftReferenceNumber(txn.getNeftReferenceNumber())
                .senderAccountNumber(txn.getSenderAccountNumber())
                .beneficiaryAccountNumber(txn.getBeneficiaryAccountNumber())
                .beneficiaryName(txn.getBeneficiaryName())
                .amount(txn.getAmount())
                .status(txn.getStatus())
                .batchId(txn.getBatchId())
                .initiatedAt(txn.getInitiatedAt())
                .completedAt(txn.getCompletedAt())
                .failureReason(txn.getFailureReason())
                .build();
    }

    private NeftBatchResponse toBatchResponse(NeftBatch batch) {
        return NeftBatchResponse.builder()
                .batchNumber(batch.getBatchNumber())
                .scheduledAt(batch.getScheduledAt())
                .processedAt(batch.getProcessedAt())
                .totalTransactions(batch.getTotalTransactions())
                .totalAmount(batch.getTotalAmount())
                .successCount(batch.getSuccessCount())
                .failureCount(batch.getFailureCount())
                .status(batch.getStatus())
                .build();
    }

    private void validatePaymentRail(String senderAccountNumber, BigDecimal amount) {
        PaymentRailConfigResponse config = accountClient.getPaymentConfig(senderAccountNumber, "NEFT");

        // Distinct from the circuit-breaker fallback (which covers account-service
        // being unreachable): this is a successful call that came back empty --
        // an account-service bug, not a network/availability problem -- but still
        // something the caller needs a diagnosable error for, not a bare NPE.
        if (config == null) {
            throw new ServiceUnavailableException(
                    "Unable to verify NEFT payment rail configuration for account " + senderAccountNumber);
        }
        if (!config.isEnabled()) {
            throw new PaymentRailNotEnabledException(
                    "NEFT transfers are not enabled for account " + senderAccountNumber + ". Please contact your branch.");
        }
        if (amount.compareTo(config.getPerTransactionLimit()) > 0) {
            throw new PerTransactionLimitExceededException(
                    "Amount ₹" + amount + " exceeds NEFT per-transaction limit of ₹" + config.getPerTransactionLimit());
        }
        if (amount.compareTo(config.getRemainingToday()) > 0) {
            throw new DailyLimitExceededException(
                    "NEFT daily limit reached. Remaining today: ₹" + config.getRemainingToday() + ". Resets at midnight.");
        }
    }
}
