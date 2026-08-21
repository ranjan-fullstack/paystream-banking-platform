package com.paystream.impsservice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.commonlib.event.PaymentInitiatedEvent;
import com.paystream.impsservice.client.AccountClient;
import com.paystream.impsservice.client.dto.AccountValidationResponse;
import com.paystream.impsservice.client.dto.PaymentRailConfigResponse;
import com.paystream.impsservice.dto.ImpsTransactionResponse;
import com.paystream.impsservice.dto.ImpsTransferRequest;
import com.paystream.impsservice.entity.ImpsTransaction;
import com.paystream.impsservice.entity.MmidRegistration;
import com.paystream.impsservice.entity.OutboxEvent;
import com.paystream.impsservice.enums.ImpsStatus;
import com.paystream.impsservice.enums.TransferMode;
import com.paystream.impsservice.exception.DailyLimitExceededException;
import com.paystream.impsservice.exception.ImpsTransactionNotFoundException;
import com.paystream.impsservice.exception.InvalidAccountException;
import com.paystream.impsservice.exception.MmidNotFoundException;
import com.paystream.impsservice.exception.PaymentRailNotEnabledException;
import com.paystream.impsservice.exception.PerTransactionLimitExceededException;
import com.paystream.impsservice.repository.ImpsTransactionRepository;
import com.paystream.impsservice.repository.MmidRegistrationRepository;
import com.paystream.impsservice.repository.OutboxEventRepository;
import com.paystream.impsservice.service.ImpsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImpsServiceImpl implements ImpsService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String IMPS_INITIATED_TOPIC = "payment.imps.initiated";

    private final ImpsTransactionRepository impsTransactionRepository;
    private final MmidRegistrationRepository mmidRegistrationRepository;
    private final AccountClient accountClient;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ImpsTransactionResponse transfer(ImpsTransferRequest request) {
        validatePaymentRail(request.getSenderAccountNumber(), request.getAmount());

        AccountValidationResponse senderValidation = accountClient.validate(request.getSenderAccountNumber());
        if (!senderValidation.isValid()) {
            throw new InvalidAccountException("Sender account is not valid: " + senderValidation.getReason());
        }

        String resolvedBeneficiaryAccountNumber = resolveBeneficiaryAccountNumber(request);

        ImpsTransaction txn = new ImpsTransaction();
        txn.setImpsReferenceNumber(generateUniqueReference());
        txn.setCustomerId(request.getCustomerId());
        txn.setTransferMode(request.getTransferMode());
        txn.setSenderAccountNumber(request.getSenderAccountNumber());
        txn.setSenderMobile(request.getSenderMobile());
        txn.setBeneficiaryAccountNumber(request.getBeneficiaryAccountNumber());
        txn.setBeneficiaryIfsc(request.getBeneficiaryIfsc());
        txn.setBeneficiaryMobile(request.getBeneficiaryMobile());
        txn.setBeneficiaryMmid(request.getBeneficiaryMmid());
        txn.setBeneficiaryName(request.getBeneficiaryName());
        txn.setAmount(request.getAmount());
        txn.setRemarks(request.getRemarks());
        txn.setStatus(ImpsStatus.PROCESSING);
        txn = impsTransactionRepository.save(txn);

        writeInitiatedEvent(txn, resolvedBeneficiaryAccountNumber);
        log.info("IMPS {} initiated, saga started", txn.getImpsReferenceNumber());

        return toResponse(txn);
    }

    @Override
    public ImpsTransactionResponse trackStatus(String referenceNumber) {
        return toResponse(findOrThrow(referenceNumber));
    }

    @Override
    public List<ImpsTransactionResponse> getHistory(String customerId) {
        return impsTransactionRepository.findByCustomerIdOrderByInitiatedAtDesc(customerId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    private void writeInitiatedEvent(ImpsTransaction txn, String resolvedBeneficiaryAccountNumber) {
        try {
            PaymentInitiatedEvent event = PaymentInitiatedEvent.builder()
                    .paymentReferenceNumber(txn.getImpsReferenceNumber())
                    .paymentMode("IMPS")
                    .senderAccountNumber(txn.getSenderAccountNumber())
                    .beneficiaryAccountNumber(resolvedBeneficiaryAccountNumber)
                    .amount(txn.getAmount())
                    .initiatedAt(Instant.now())
                    .build();

            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setAggregateType("ImpsTransaction");
            outboxEvent.setAggregateId(txn.getImpsReferenceNumber());
            outboxEvent.setEventType("PaymentInitiated");
            outboxEvent.setTopic(IMPS_INITIATED_TOPIC);
            outboxEvent.setPayload(objectMapper.writeValueAsString(event));
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to write initiated outbox event for IMPS {}", txn.getImpsReferenceNumber(), e);
            throw new RuntimeException("Failed to write outbox event", e);
        }
    }

    private String resolveBeneficiaryAccountNumber(ImpsTransferRequest request) {
        if (request.getTransferMode() == TransferMode.ACCOUNT_IFSC) {
            return request.getBeneficiaryAccountNumber();
        }
        MmidRegistration registration = mmidRegistrationRepository.findByMobileNumber(request.getBeneficiaryMobile())
                .filter(r -> r.getMmid().equals(request.getBeneficiaryMmid()) && r.isActive())
                .orElseThrow(() -> new MmidNotFoundException(request.getBeneficiaryMobile()));
        return registration.getAccountNumber();
    }

    private ImpsTransaction findOrThrow(String referenceNumber) {
        return impsTransactionRepository.findByImpsReferenceNumber(referenceNumber)
                .orElseThrow(() -> new ImpsTransactionNotFoundException(referenceNumber));
    }

    private String generateUniqueReference() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "IMPS" + Instant.now().toEpochMilli() + RANDOM.nextInt(1000);
            if (!impsTransactionRepository.existsByImpsReferenceNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique IMPS reference number, please retry");
    }

    private ImpsTransactionResponse toResponse(ImpsTransaction txn) {
        return ImpsTransactionResponse.builder()
                .impsReferenceNumber(txn.getImpsReferenceNumber())
                .transferMode(txn.getTransferMode())
                .senderAccountNumber(txn.getSenderAccountNumber())
                .beneficiaryName(txn.getBeneficiaryName())
                .amount(txn.getAmount())
                .status(txn.getStatus())
                .rrn(txn.getRrn())
                .initiatedAt(txn.getInitiatedAt())
                .completedAt(txn.getCompletedAt())
                .failureReason(txn.getFailureReason())
                .build();
    }

    private void validatePaymentRail(String senderAccountNumber, BigDecimal amount) {
        PaymentRailConfigResponse config = accountClient.getPaymentConfig(senderAccountNumber, "IMPS");

        if (!config.isEnabled()) {
            throw new PaymentRailNotEnabledException(
                    "IMPS transfers are not enabled for account " + senderAccountNumber + ". Please contact your branch.");
        }
        if (amount.compareTo(config.getPerTransactionLimit()) > 0) {
            throw new PerTransactionLimitExceededException(
                    "Amount ₹" + amount + " exceeds IMPS per-transaction limit of ₹" + config.getPerTransactionLimit());
        }
        if (amount.compareTo(config.getRemainingToday()) > 0) {
            throw new DailyLimitExceededException(
                    "IMPS daily limit reached. Remaining today: ₹" + config.getRemainingToday() + ". Resets at midnight.");
        }
    }
}
