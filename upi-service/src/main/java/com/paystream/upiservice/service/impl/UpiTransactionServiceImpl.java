package com.paystream.upiservice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.commonlib.event.PaymentInitiatedEvent;
import com.paystream.upiservice.client.AccountClient;
import com.paystream.upiservice.client.dto.AccountValidationResponse;
import com.paystream.upiservice.client.dto.PaymentRailConfigResponse;
import com.paystream.upiservice.dto.*;
import com.paystream.upiservice.entity.OutboxEvent;
import com.paystream.upiservice.entity.UpiCollectRequest;
import com.paystream.upiservice.entity.UpiTransaction;
import com.paystream.upiservice.entity.VirtualPaymentAddress;
import com.paystream.upiservice.enums.CollectRequestStatus;
import com.paystream.upiservice.enums.UpiTransactionStatus;
import com.paystream.upiservice.enums.UpiTransactionType;
import com.paystream.upiservice.exception.*;
import com.paystream.upiservice.repository.OutboxEventRepository;
import com.paystream.upiservice.repository.UpiCollectRequestRepository;
import com.paystream.upiservice.repository.UpiTransactionRepository;
import com.paystream.upiservice.repository.VpaRepository;
import com.paystream.upiservice.service.UpiTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpiTransactionServiceImpl implements UpiTransactionService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String UPI_INITIATED_TOPIC = "payment.upi.initiated";

    private final UpiTransactionRepository upiTransactionRepository;
    private final UpiCollectRequestRepository upiCollectRequestRepository;
    private final VpaRepository vpaRepository;
    private final AccountClient accountClient;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public UpiTransactionResponse pay(UpiPayRequest request) {
        VirtualPaymentAddress sender = findVpaOrThrow(request.getSenderVpa());
        VirtualPaymentAddress receiver = findVpaOrThrow(request.getReceiverVpa());
        verifyPin(sender, request.getUpiPin());

        validatePaymentRail(sender.getAccountNumber(), request.getAmount());

        AccountValidationResponse senderValidation = accountClient.validate(sender.getAccountNumber());
        if (!senderValidation.isValid()) {
            throw new InvalidAccountException("Sender account is not valid: " + senderValidation.getReason());
        }

        UpiTransaction txn = new UpiTransaction();
        txn.setUpiTransactionId(generateUniqueTransactionId());
        txn.setTransactionType(UpiTransactionType.PAY);
        txn.setSenderVpa(sender.getVpa());
        txn.setReceiverVpa(receiver.getVpa());
        txn.setAmount(request.getAmount());
        txn.setRemarks(request.getRemarks());
        txn.setStatus(UpiTransactionStatus.PROCESSING);
        txn = upiTransactionRepository.save(txn);

        writeInitiatedEvent(txn, sender.getAccountNumber(), receiver.getAccountNumber());
        return toResponse(txn);
    }

    @Override
    @Transactional
    public UpiTransactionResponse collect(UpiCollectMoneyRequest request) {
        VirtualPaymentAddress payee = findVpaOrThrow(request.getRequestedByVpa());
        VirtualPaymentAddress payer = findVpaOrThrow(request.getRequestedFromVpa());

        validatePaymentRail(payer.getAccountNumber(), request.getAmount());

        UpiTransaction txn = new UpiTransaction();
        txn.setUpiTransactionId(generateUniqueTransactionId());
        txn.setTransactionType(UpiTransactionType.COLLECT);
        txn.setSenderVpa(payer.getVpa());
        txn.setReceiverVpa(payee.getVpa());
        txn.setAmount(request.getAmount());
        txn.setRemarks(request.getRemarks());
        txn.setStatus(UpiTransactionStatus.PENDING_PIN);
        txn.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        txn = upiTransactionRepository.save(txn);

        UpiCollectRequest collectRequest = new UpiCollectRequest();
        collectRequest.setUpiTransaction(txn);
        collectRequest.setRequestedBy(payee.getVpa());
        collectRequest.setRequestedFrom(payer.getVpa());
        collectRequest.setExpiresAt(txn.getExpiresAt());
        collectRequest.setStatus(CollectRequestStatus.PENDING);
        upiCollectRequestRepository.save(collectRequest);

        return toResponse(txn);
    }

    @Override
    @Transactional
    public UpiTransactionResponse respondToCollect(String upiTransactionId, CollectRespondRequest request) {
        UpiTransaction txn = findTransactionOrThrow(upiTransactionId);
        UpiCollectRequest collectRequest = upiCollectRequestRepository.findByUpiTransactionId(txn.getId())
                .orElseThrow(() -> new UpiTransactionNotFoundException(upiTransactionId));

        if (LocalDateTime.now().isAfter(txn.getExpiresAt())) {
            txn.setStatus(UpiTransactionStatus.EXPIRED);
            collectRequest.setStatus(CollectRequestStatus.EXPIRED);
            upiTransactionRepository.save(txn);
            upiCollectRequestRepository.save(collectRequest);
            throw new CollectRequestExpiredException(upiTransactionId);
        }

        if (!request.isAccept()) {
            txn.setStatus(UpiTransactionStatus.DECLINED);
            collectRequest.setStatus(CollectRequestStatus.DECLINED);
            upiCollectRequestRepository.save(collectRequest);
            return toResponse(upiTransactionRepository.save(txn));
        }

        VirtualPaymentAddress payer = findVpaOrThrow(txn.getSenderVpa());
        VirtualPaymentAddress payee = findVpaOrThrow(txn.getReceiverVpa());
        verifyPin(payer, request.getUpiPin());

        txn.setStatus(UpiTransactionStatus.PROCESSING);
        upiTransactionRepository.save(txn);
        collectRequest.setStatus(CollectRequestStatus.ACCEPTED);
        upiCollectRequestRepository.save(collectRequest);

        writeInitiatedEvent(txn, payer.getAccountNumber(), payee.getAccountNumber());
        return toResponse(txn);
    }

    @Override
    @Transactional
    public UpiTransactionResponse refund(RefundRequest request) {
        UpiTransaction original = findTransactionOrThrow(request.getOriginalUpiTransactionId());
        if (original.getStatus() != UpiTransactionStatus.COMPLETED) {
            throw new IllegalStateException("Only completed transactions can be refunded");
        }

        VirtualPaymentAddress refundSender = findVpaOrThrow(original.getReceiverVpa());
        VirtualPaymentAddress refundReceiver = findVpaOrThrow(original.getSenderVpa());

        UpiTransaction refundTxn = new UpiTransaction();
        refundTxn.setUpiTransactionId(generateUniqueTransactionId());
        refundTxn.setTransactionType(UpiTransactionType.REFUND);
        refundTxn.setSenderVpa(refundSender.getVpa());
        refundTxn.setReceiverVpa(refundReceiver.getVpa());
        refundTxn.setAmount(request.getAmount());
        refundTxn.setRemarks(request.getReason());
        refundTxn.setStatus(UpiTransactionStatus.PROCESSING);
        refundTxn = upiTransactionRepository.save(refundTxn);

        writeInitiatedEvent(refundTxn, refundSender.getAccountNumber(), refundReceiver.getAccountNumber());
        return toResponse(refundTxn);
    }

    @Override
    public UpiTransactionResponse getStatus(String upiTransactionId) {
        return toResponse(findTransactionOrThrow(upiTransactionId));
    }

    @Override
    public List<UpiTransactionResponse> getHistory(String vpa) {
        return upiTransactionRepository.findBySenderVpaOrReceiverVpaOrderByInitiatedAtDesc(vpa, vpa)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    private void writeInitiatedEvent(UpiTransaction txn, String senderAccountNumber, String beneficiaryAccountNumber) {
        try {
            PaymentInitiatedEvent event = PaymentInitiatedEvent.builder()
                    .paymentReferenceNumber(txn.getUpiTransactionId())
                    .paymentMode("UPI")
                    .senderAccountNumber(senderAccountNumber)
                    .beneficiaryAccountNumber(beneficiaryAccountNumber)
                    .amount(txn.getAmount())
                    .initiatedAt(Instant.now())
                    .build();

            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setAggregateType("UpiTransaction");
            outboxEvent.setAggregateId(txn.getUpiTransactionId());
            outboxEvent.setEventType("PaymentInitiated");
            outboxEvent.setTopic(UPI_INITIATED_TOPIC);
            outboxEvent.setPayload(objectMapper.writeValueAsString(event));
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to write outbox event for UPI transaction {}", txn.getUpiTransactionId(), e);
            throw new RuntimeException("Failed to initiate UPI payment", e);
        }
    }

    private void verifyPin(VirtualPaymentAddress vpa, String rawPin) {
        if (vpa.getUpiPin() == null) {
            throw new InvalidPinException();
        }
        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        if (!encoder.matches(rawPin, vpa.getUpiPin())) {
            throw new InvalidPinException();
        }
    }

    private VirtualPaymentAddress findVpaOrThrow(String vpa) {
        return vpaRepository.findByVpa(vpa).orElseThrow(() -> new VpaNotFoundException(vpa));
    }

    private UpiTransaction findTransactionOrThrow(String upiTransactionId) {
        return upiTransactionRepository.findByUpiTransactionId(upiTransactionId)
                .orElseThrow(() -> new UpiTransactionNotFoundException(upiTransactionId));
    }

    private String generateUniqueTransactionId() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "UPI" + Instant.now().toEpochMilli() + RANDOM.nextInt(1000);
            if (!upiTransactionRepository.existsByUpiTransactionId(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique UPI transaction id, please retry");
    }

    private UpiTransactionResponse toResponse(UpiTransaction txn) {
        return UpiTransactionResponse.builder()
                .upiTransactionId(txn.getUpiTransactionId())
                .transactionType(txn.getTransactionType())
                .senderVpa(txn.getSenderVpa())
                .receiverVpa(txn.getReceiverVpa())
                .amount(txn.getAmount())
                .status(txn.getStatus())
                .npciTransactionId(txn.getNpciTransactionId())
                .initiatedAt(txn.getInitiatedAt())
                .completedAt(txn.getCompletedAt())
                .expiresAt(txn.getExpiresAt())
                .failureReason(txn.getFailureReason())
                .build();
    }

    private void validatePaymentRail(String accountNumber, BigDecimal amount) {
        PaymentRailConfigResponse config = accountClient.getPaymentConfig(accountNumber, "UPI");

        // Distinct from the circuit-breaker fallback (which covers account-service
        // being unreachable): this is a successful call that came back empty --
        // an account-service bug, not a network/availability problem -- but still
        // something the caller needs a diagnosable error for, not a bare NPE.
        if (config == null) {
            throw new ServiceUnavailableException(
                    "Unable to verify UPI payment rail configuration for account " + accountNumber);
        }
        if (!config.isEnabled()) {
            throw new PaymentRailNotEnabledException(
                    "UPI transfers are not enabled for account " + accountNumber + ". Please contact your branch.");
        }
        if (amount.compareTo(config.getPerTransactionLimit()) > 0) {
            throw new PerTransactionLimitExceededException(
                    "Amount ₹" + amount + " exceeds UPI per-transaction limit of ₹" + config.getPerTransactionLimit());
        }
        if (amount.compareTo(config.getRemainingToday()) > 0) {
            throw new DailyLimitExceededException(
                    "UPI daily limit reached. Remaining today: ₹" + config.getRemainingToday() + ". Resets at midnight.");
        }
    }
}
