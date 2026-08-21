package com.paystream.rtgsservice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.commonlib.event.PaymentInitiatedEvent;
import com.paystream.rtgsservice.client.AccountClient;
import com.paystream.rtgsservice.client.dto.AccountValidationResponse;
import com.paystream.rtgsservice.client.dto.PaymentRailConfigResponse;
import com.paystream.rtgsservice.dto.RtgsTransactionResponse;
import com.paystream.rtgsservice.dto.RtgsTransferRequest;
import com.paystream.rtgsservice.entity.OutboxEvent;
import com.paystream.rtgsservice.entity.RtgsTransaction;
import com.paystream.rtgsservice.enums.RtgsStatus;
import com.paystream.rtgsservice.exception.DailyLimitExceededException;
import com.paystream.rtgsservice.exception.InvalidAccountException;
import com.paystream.rtgsservice.exception.PaymentRailNotEnabledException;
import com.paystream.rtgsservice.exception.PerTransactionLimitExceededException;
import com.paystream.rtgsservice.exception.RtgsAlreadySettledException;
import com.paystream.rtgsservice.exception.RtgsTransactionNotFoundException;
import com.paystream.rtgsservice.exception.RtgsWindowClosedException;
import com.paystream.rtgsservice.repository.OutboxEventRepository;
import com.paystream.rtgsservice.repository.RtgsTransactionRepository;
import com.paystream.rtgsservice.service.RtgsService;
import com.paystream.rtgsservice.util.RtgsWindowValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RtgsServiceImpl implements RtgsService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String RTGS_INITIATED_TOPIC = "payment.rtgs.initiated";

    private final RtgsTransactionRepository rtgsTransactionRepository;
    private final AccountClient accountClient;
    private final RtgsWindowValidator windowValidator;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public RtgsTransactionResponse initiateTransfer(RtgsTransferRequest request) {
        validatePaymentRail(request.getSenderAccountNumber(), request.getAmount());

        if (!windowValidator.isWithinWindow(LocalDateTime.now())) {
            throw new RtgsWindowClosedException();
        }

        AccountValidationResponse senderValidation = accountClient.validate(request.getSenderAccountNumber());
        if (!senderValidation.isValid()) {
            throw new InvalidAccountException("Sender account is not valid: " + senderValidation.getReason());
        }

        RtgsTransaction txn = new RtgsTransaction();
        txn.setRtgsReferenceNumber(generateUniqueReference());
        txn.setCustomerId(request.getCustomerId());
        txn.setSenderAccountNumber(request.getSenderAccountNumber());
        txn.setSenderIfsc(request.getSenderIfsc());
        txn.setBeneficiaryAccountNumber(request.getBeneficiaryAccountNumber());
        txn.setBeneficiaryIfsc(request.getBeneficiaryIfsc());
        txn.setBeneficiaryName(request.getBeneficiaryName());
        txn.setAmount(request.getAmount());
        txn.setPurpose(request.getPurpose());
        txn.setStatus(RtgsStatus.PROCESSING);
        txn = rtgsTransactionRepository.save(txn);

        writeInitiatedEvent(txn);
        log.info("RTGS {} initiated, saga started", txn.getRtgsReferenceNumber());

        return toResponse(txn);
    }

    @Override
    public RtgsTransactionResponse trackStatus(String referenceNumber) {
        return toResponse(findOrThrow(referenceNumber));
    }

    @Override
    public List<RtgsTransactionResponse> getHistory(String customerId) {
        return rtgsTransactionRepository.findByCustomerIdOrderByInitiatedAtDesc(customerId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RtgsTransactionResponse recall(String referenceNumber) {
        RtgsTransaction txn = findOrThrow(referenceNumber);
        if (txn.getStatus() == RtgsStatus.COMPLETED) {
            throw new RtgsAlreadySettledException(referenceNumber);
        }
        txn.setStatus(RtgsStatus.RETURNED);
        return toResponse(rtgsTransactionRepository.save(txn));
    }

    private void writeInitiatedEvent(RtgsTransaction txn) {
        try {
            PaymentInitiatedEvent event = PaymentInitiatedEvent.builder()
                    .paymentReferenceNumber(txn.getRtgsReferenceNumber())
                    .paymentMode("RTGS")
                    .senderAccountNumber(txn.getSenderAccountNumber())
                    .beneficiaryAccountNumber(txn.getBeneficiaryAccountNumber())
                    .amount(txn.getAmount())
                    .initiatedAt(Instant.now())
                    .build();

            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setAggregateType("RtgsTransaction");
            outboxEvent.setAggregateId(txn.getRtgsReferenceNumber());
            outboxEvent.setEventType("PaymentInitiated");
            outboxEvent.setTopic(RTGS_INITIATED_TOPIC);
            outboxEvent.setPayload(objectMapper.writeValueAsString(event));
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to write initiated outbox event for RTGS {}", txn.getRtgsReferenceNumber(), e);
            throw new RuntimeException("Failed to write outbox event", e);
        }
    }

    private RtgsTransaction findOrThrow(String referenceNumber) {
        return rtgsTransactionRepository.findByRtgsReferenceNumber(referenceNumber)
                .orElseThrow(() -> new RtgsTransactionNotFoundException(referenceNumber));
    }

    private String generateUniqueReference() {
        String datePart = LocalDateTime.now().format(DATE_FMT);
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "RTGS" + datePart + String.format("%04d", RANDOM.nextInt(10000));
            if (!rtgsTransactionRepository.existsByRtgsReferenceNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique RTGS reference number, please retry");
    }

    private RtgsTransactionResponse toResponse(RtgsTransaction txn) {
        return RtgsTransactionResponse.builder()
                .rtgsReferenceNumber(txn.getRtgsReferenceNumber())
                .senderAccountNumber(txn.getSenderAccountNumber())
                .beneficiaryAccountNumber(txn.getBeneficiaryAccountNumber())
                .beneficiaryName(txn.getBeneficiaryName())
                .amount(txn.getAmount())
                .purpose(txn.getPurpose())
                .status(txn.getStatus())
                .initiatedAt(txn.getInitiatedAt())
                .settledAt(txn.getSettledAt())
                .rbiUtrNumber(txn.getRbiUtrNumber())
                .failureReason(txn.getFailureReason())
                .build();
    }

    private void validatePaymentRail(String senderAccountNumber, BigDecimal amount) {
        PaymentRailConfigResponse config = accountClient.getPaymentConfig(senderAccountNumber, "RTGS");

        if (!config.isEnabled()) {
            throw new PaymentRailNotEnabledException(
                    "RTGS transfers are not enabled for account " + senderAccountNumber + ". Please contact your branch.");
        }
        if (amount.compareTo(config.getPerTransactionLimit()) > 0) {
            throw new PerTransactionLimitExceededException(
                    "Amount ₹" + amount + " exceeds RTGS per-transaction limit of ₹" + config.getPerTransactionLimit());
        }
        if (amount.compareTo(config.getRemainingToday()) > 0) {
            throw new DailyLimitExceededException(
                    "RTGS daily limit reached. Remaining today: ₹" + config.getRemainingToday() + ". Resets at midnight.");
        }
    }
}
