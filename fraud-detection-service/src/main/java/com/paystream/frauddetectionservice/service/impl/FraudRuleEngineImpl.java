package com.paystream.frauddetectionservice.service.impl;

import com.paystream.frauddetectionservice.entity.FraudRule;
import com.paystream.frauddetectionservice.entity.TransactionLog;
import com.paystream.frauddetectionservice.enums.RuleType;
import com.paystream.frauddetectionservice.repository.FraudRuleRepository;
import com.paystream.frauddetectionservice.repository.TransactionLogRepository;
import com.paystream.frauddetectionservice.service.FraudRuleEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FraudRuleEngineImpl implements FraudRuleEngine {

    private static final LocalTime BUSINESS_OPEN = LocalTime.of(8, 0);
    private static final LocalTime BUSINESS_CLOSE = LocalTime.of(19, 0);

    private final FraudRuleRepository fraudRuleRepository;
    private final TransactionLogRepository transactionLogRepository;

    @Override
    public Optional<RuleEvaluationResult> evaluate(TransactionLog txn) {
        Optional<RuleEvaluationResult> result;

        if ((result = checkVelocity(txn)).isPresent()) return result;
        if ((result = checkAmountAnomaly(txn)).isPresent()) return result;
        if ((result = checkOddHours(txn)).isPresent()) return result;
        if ((result = checkDuplicateTransaction(txn)).isPresent()) return result;
        if ((result = checkHighRiskAccount(txn)).isPresent()) return result;
        if ((result = checkDailyLimitBreach(txn)).isPresent()) return result;
        if ((result = checkCtr(txn)).isPresent()) return result;
        if ((result = checkStructuring(txn)).isPresent()) return result;

        return Optional.empty();
    }

    private Optional<RuleEvaluationResult> checkVelocity(TransactionLog txn) {
        FraudRule rule = activeRule(RuleType.VELOCITY);
        if (rule == null) return Optional.empty();

        LocalDateTime since = txn.getOccurredAt().minusMinutes(10);
        long count = transactionLogRepository.findByAccountNumberAndOccurredAtAfter(txn.getAccountNumber(), since).size();

        if (count > rule.getThreshold().intValue()) {
            return Optional.of(new RuleEvaluationResult("VELOCITY_CHECK", 60, rule.getAction().name()));
        }
        return Optional.empty();
    }

    private Optional<RuleEvaluationResult> checkAmountAnomaly(TransactionLog txn) {
        FraudRule rule = activeRule(RuleType.AMOUNT_ANOMALY);
        if (rule == null) return Optional.empty();

        List<TransactionLog> history = transactionLogRepository
                .findByAccountNumberAndOccurredAtAfter(txn.getAccountNumber(), txn.getOccurredAt().minusDays(90));
        history.removeIf(t -> t.getId().equals(txn.getId()));

        if (history.size() < 3) return Optional.empty();

        BigDecimal sum = history.stream().map(TransactionLog::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = sum.divide(BigDecimal.valueOf(history.size()), 4, java.math.RoundingMode.HALF_UP);
        BigDecimal anomalyThreshold = average.multiply(rule.getThreshold());

        if (txn.getAmount().compareTo(anomalyThreshold) > 0) {
            return Optional.of(new RuleEvaluationResult("AMOUNT_ANOMALY", 50, rule.getAction().name()));
        }
        return Optional.empty();
    }

    private Optional<RuleEvaluationResult> checkOddHours(TransactionLog txn) {
        FraudRule rule = activeRule(RuleType.ODD_HOURS);
        if (rule == null) return Optional.empty();

        boolean isNeftOrRtgs = "NEFT".equals(txn.getPaymentMode()) || "RTGS".equals(txn.getPaymentMode());
        LocalTime time = txn.getOccurredAt().toLocalTime();
        boolean outsideHours = time.isBefore(BUSINESS_OPEN) || time.isAfter(BUSINESS_CLOSE);

        if (isNeftOrRtgs && outsideHours) {
            return Optional.of(new RuleEvaluationResult("ODD_HOURS", 80, rule.getAction().name()));
        }
        return Optional.empty();
    }

    private Optional<RuleEvaluationResult> checkDuplicateTransaction(TransactionLog txn) {
        FraudRule rule = activeRule(RuleType.DUPLICATE_TRANSACTION);
        if (rule == null) return Optional.empty();

        LocalDateTime since = txn.getOccurredAt().minusMinutes(rule.getThreshold().intValue());
        long count = transactionLogRepository.findByAccountNumberAndBeneficiaryAccountNumberAndAmountAndOccurredAtAfter(
                txn.getAccountNumber(), txn.getBeneficiaryAccountNumber(), txn.getAmount(), since).size();

        if (count > 1) {
            return Optional.of(new RuleEvaluationResult("DUPLICATE_TRANSACTION", 70, rule.getAction().name()));
        }
        return Optional.empty();
    }

    private Optional<RuleEvaluationResult> checkHighRiskAccount(TransactionLog txn) {
        // Requires a customer risk-rating feed (customer-service) that is not
        // currently wired into this service's event stream. Always inactive
        // until that integration exists.
        return Optional.empty();
    }

    private Optional<RuleEvaluationResult> checkDailyLimitBreach(TransactionLog txn) {
        FraudRule rule = activeRule(RuleType.DAILY_LIMIT_BREACH);
        if (rule == null) return Optional.empty();

        LocalDateTime midnight = txn.getOccurredAt().toLocalDate().atStartOfDay();
        BigDecimal totalToday = transactionLogRepository
                .findByAccountNumberAndOccurredAtAfter(txn.getAccountNumber(), midnight)
                .stream().map(TransactionLog::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalToday.compareTo(rule.getThreshold()) > 0) {
            return Optional.of(new RuleEvaluationResult("DAILY_LIMIT_BREACH", 75, rule.getAction().name()));
        }
        return Optional.empty();
    }

    private Optional<RuleEvaluationResult> checkCtr(TransactionLog txn) {
        FraudRule rule = activeRule(RuleType.CTR);
        if (rule == null) return Optional.empty();

        if (txn.getAmount().compareTo(rule.getThreshold()) >= 0) {
            return Optional.of(new RuleEvaluationResult("CTR_THRESHOLD", 90, rule.getAction().name()));
        }
        return Optional.empty();
    }

    private Optional<RuleEvaluationResult> checkStructuring(TransactionLog txn) {
        FraudRule rule = activeRule(RuleType.STRUCTURING);
        if (rule == null) return Optional.empty();

        BigDecimal threshold = rule.getThreshold();
        BigDecimal floor = threshold.multiply(new BigDecimal("0.85"));

        LocalDateTime since = txn.getOccurredAt().minusHours(24);
        long count = transactionLogRepository
                .findByAccountNumberAndOccurredAtAfter(txn.getAccountNumber(), since)
                .stream()
                .filter(t -> t.getAmount().compareTo(floor) >= 0 && t.getAmount().compareTo(threshold) < 0)
                .count();

        if (count >= 3) {
            return Optional.of(new RuleEvaluationResult("STRUCTURING", 85, rule.getAction().name()));
        }
        return Optional.empty();
    }

    private FraudRule activeRule(RuleType type) {
        return fraudRuleRepository.findByRuleType(type).filter(FraudRule::isActive).orElse(null);
    }
}
