package com.paystream.frauddetectionservice.service;

import com.paystream.frauddetectionservice.entity.FraudRule;
import com.paystream.frauddetectionservice.entity.TransactionLog;
import com.paystream.frauddetectionservice.enums.RuleAction;
import com.paystream.frauddetectionservice.enums.RuleType;
import com.paystream.frauddetectionservice.repository.FraudRuleRepository;
import com.paystream.frauddetectionservice.repository.TransactionLogRepository;
import com.paystream.frauddetectionservice.service.impl.FraudRuleEngineImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FraudRuleEngine unit tests")
class FraudRuleEngineTest {

    @Mock
    private FraudRuleRepository fraudRuleRepository;

    @Mock
    private TransactionLogRepository transactionLogRepository;

    @InjectMocks
    private FraudRuleEngineImpl fraudRuleEngine;

    private TransactionLog buildTxn(String accountNumber, String beneficiaryAccountNumber, BigDecimal amount, LocalDateTime occurredAt) {
        TransactionLog txn = new TransactionLog();
        txn.setId(UUID.randomUUID());
        txn.setAccountNumber(accountNumber);
        txn.setBeneficiaryAccountNumber(beneficiaryAccountNumber);
        txn.setAmount(amount);
        txn.setPaymentMode("IMPS");
        txn.setPaymentReferenceNumber("REF-" + txn.getId());
        txn.setOccurredAt(occurredAt);
        return txn;
    }

    private FraudRule buildRule(RuleType type, BigDecimal threshold, RuleAction action) {
        FraudRule rule = new FraudRule();
        rule.setId(UUID.randomUUID());
        rule.setRuleName(type.name() + "_RULE");
        rule.setRuleType(type);
        rule.setThreshold(threshold);
        rule.setAction(action);
        rule.setActive(true);
        return rule;
    }

    private void stubOnlyActiveRule(RuleType type, FraudRule rule) {
        lenient().when(fraudRuleRepository.findByRuleType(any())).thenReturn(Optional.empty());
        when(fraudRuleRepository.findByRuleType(type)).thenReturn(Optional.of(rule));
    }

    private List<TransactionLog> nLogs(int n, String accountNumber) {
        List<TransactionLog> logs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            logs.add(buildTxn(accountNumber, "9999888877776666", new BigDecimal("5000"), LocalDateTime.now()));
        }
        return logs;
    }

    @Test
    @DisplayName("Should not raise an alert when fewer than the velocity threshold of transactions occurred recently")
    void testVelocityCheck_under5Transactions_noAlert() {
        // Given
        String accountNumber = "1111222233334444";
        TransactionLog txn = buildTxn(accountNumber, "9999888877776666", new BigDecimal("5000"), LocalDateTime.now());
        FraudRule velocityRule = buildRule(RuleType.VELOCITY, new BigDecimal("5"), RuleAction.ALERT);

        stubOnlyActiveRule(RuleType.VELOCITY, velocityRule);
        when(transactionLogRepository.findByAccountNumberAndOccurredAtAfter(eq(accountNumber), any()))
                .thenReturn(nLogs(4, accountNumber));

        // When
        Optional<FraudRuleEngine.RuleEvaluationResult> result = fraudRuleEngine.evaluate(txn);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should raise a velocity alert when more than 5 transactions occur within 10 minutes")
    void testVelocityCheck_over5Transactions_raisesAlert() {
        // Given
        String accountNumber = "1111222233334444";
        TransactionLog txn = buildTxn(accountNumber, "9999888877776666", new BigDecimal("5000"), LocalDateTime.now());
        FraudRule velocityRule = buildRule(RuleType.VELOCITY, new BigDecimal("5"), RuleAction.ALERT);

        stubOnlyActiveRule(RuleType.VELOCITY, velocityRule);
        when(transactionLogRepository.findByAccountNumberAndOccurredAtAfter(eq(accountNumber), any()))
                .thenReturn(nLogs(6, accountNumber));

        // When
        Optional<FraudRuleEngine.RuleEvaluationResult> result = fraudRuleEngine.evaluate(txn);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().ruleTriggered()).isEqualTo("VELOCITY_CHECK");
        assertThat(result.get().action()).isEqualTo("ALERT");
    }

    @Test
    @DisplayName("Should not raise an alert when the transaction amount is close to the account's historical average")
    void testAmountAnomaly_normal_noAlert() {
        // Given
        String accountNumber = "1111222233334444";
        LocalDateTime now = LocalDateTime.now();
        TransactionLog txn = buildTxn(accountNumber, "9999888877776666", new BigDecimal("2000"), now);
        FraudRule anomalyRule = buildRule(RuleType.AMOUNT_ANOMALY, new BigDecimal("3"), RuleAction.ALERT);

        List<TransactionLog> history = List.of(
                buildTxn(accountNumber, "9999888877776666", new BigDecimal("1000"), now.minusDays(1)),
                buildTxn(accountNumber, "9999888877776666", new BigDecimal("1000"), now.minusDays(2)),
                buildTxn(accountNumber, "9999888877776666", new BigDecimal("1000"), now.minusDays(3))
        );

        stubOnlyActiveRule(RuleType.AMOUNT_ANOMALY, anomalyRule);
        when(transactionLogRepository.findByAccountNumberAndOccurredAtAfter(eq(accountNumber), any()))
                .thenReturn(new ArrayList<>(history));

        // When
        Optional<FraudRuleEngine.RuleEvaluationResult> result = fraudRuleEngine.evaluate(txn);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should raise an alert when the transaction amount exceeds 3x the historical average")
    void testAmountAnomaly_3xAverage_raisesAlert() {
        // Given - history averages Rs. 1000, so the 3x anomaly threshold is Rs. 3000
        String accountNumber = "1111222233334444";
        LocalDateTime now = LocalDateTime.now();
        TransactionLog txn = buildTxn(accountNumber, "9999888877776666", new BigDecimal("3500"), now);
        FraudRule anomalyRule = buildRule(RuleType.AMOUNT_ANOMALY, new BigDecimal("3"), RuleAction.ALERT);

        List<TransactionLog> history = List.of(
                buildTxn(accountNumber, "9999888877776666", new BigDecimal("1000"), now.minusDays(1)),
                buildTxn(accountNumber, "9999888877776666", new BigDecimal("1000"), now.minusDays(2)),
                buildTxn(accountNumber, "9999888877776666", new BigDecimal("1000"), now.minusDays(3))
        );

        stubOnlyActiveRule(RuleType.AMOUNT_ANOMALY, anomalyRule);
        when(transactionLogRepository.findByAccountNumberAndOccurredAtAfter(eq(accountNumber), any()))
                .thenReturn(new ArrayList<>(history));

        // When
        Optional<FraudRuleEngine.RuleEvaluationResult> result = fraudRuleEngine.evaluate(txn);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().ruleTriggered()).isEqualTo("AMOUNT_ANOMALY");
    }

    @Test
    @DisplayName("Should raise an alert for a duplicate transaction with the same amount and beneficiary")
    void testDuplicateTransaction_sameAmountSameBeneficiary_raisesAlert() {
        // Given
        String accountNumber = "1111222233334444";
        String beneficiary = "9999888877776666";
        BigDecimal amount = new BigDecimal("5000");
        TransactionLog txn = buildTxn(accountNumber, beneficiary, amount, LocalDateTime.now());
        FraudRule duplicateRule = buildRule(RuleType.DUPLICATE_TRANSACTION, new BigDecimal("10"), RuleAction.ALERT);

        stubOnlyActiveRule(RuleType.DUPLICATE_TRANSACTION, duplicateRule);
        when(transactionLogRepository.findByAccountNumberAndBeneficiaryAccountNumberAndAmountAndOccurredAtAfter(
                eq(accountNumber), eq(beneficiary), eq(amount), any()))
                .thenReturn(List.of(txn, buildTxn(accountNumber, beneficiary, amount, LocalDateTime.now().minusMinutes(2))));

        // When
        Optional<FraudRuleEngine.RuleEvaluationResult> result = fraudRuleEngine.evaluate(txn);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().ruleTriggered()).isEqualTo("DUPLICATE_TRANSACTION");
    }

    @Test
    @DisplayName("Should not raise a duplicate-transaction alert when no matching transfer to that beneficiary exists")
    void testDuplicateTransaction_differentBeneficiary_noAlert() {
        // Given
        String accountNumber = "1111222233334444";
        String beneficiary = "9999888877776666";
        BigDecimal amount = new BigDecimal("5000");
        TransactionLog txn = buildTxn(accountNumber, beneficiary, amount, LocalDateTime.now());
        FraudRule duplicateRule = buildRule(RuleType.DUPLICATE_TRANSACTION, new BigDecimal("10"), RuleAction.ALERT);

        stubOnlyActiveRule(RuleType.DUPLICATE_TRANSACTION, duplicateRule);
        when(transactionLogRepository.findByAccountNumberAndBeneficiaryAccountNumberAndAmountAndOccurredAtAfter(
                eq(accountNumber), eq(beneficiary), eq(amount), any()))
                .thenReturn(List.of(txn));

        // When
        Optional<FraudRuleEngine.RuleEvaluationResult> result = fraudRuleEngine.evaluate(txn);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should raise an odd-hours alert for a NEFT transaction occurring outside business hours")
    void testOddHours_NeftOutsideBusinessHours_raisesAlert() {
        // Given
        String accountNumber = "1111222233334444";
        TransactionLog txn = buildTxn(accountNumber, "9999888877776666", new BigDecimal("5000"),
                LocalDateTime.now().withHour(23).withMinute(30));
        txn.setPaymentMode("NEFT");
        FraudRule oddHoursRule = buildRule(RuleType.ODD_HOURS, BigDecimal.ZERO, RuleAction.ALERT);

        stubOnlyActiveRule(RuleType.ODD_HOURS, oddHoursRule);

        // When
        Optional<FraudRuleEngine.RuleEvaluationResult> result = fraudRuleEngine.evaluate(txn);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().ruleTriggered()).isEqualTo("ODD_HOURS");
    }

    @Test
    @DisplayName("Should not raise an odd-hours alert for an IMPS transaction at night, since IMPS is 24x7")
    void testOddHours_ImpsOutsideBusinessHours_noAlert() {
        // Given
        String accountNumber = "1111222233334444";
        TransactionLog txn = buildTxn(accountNumber, "9999888877776666", new BigDecimal("5000"),
                LocalDateTime.now().withHour(23).withMinute(30));
        txn.setPaymentMode("IMPS");
        FraudRule oddHoursRule = buildRule(RuleType.ODD_HOURS, BigDecimal.ZERO, RuleAction.ALERT);

        stubOnlyActiveRule(RuleType.ODD_HOURS, oddHoursRule);

        // When
        Optional<FraudRuleEngine.RuleEvaluationResult> result = fraudRuleEngine.evaluate(txn);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("CTR: should raise REVIEW alert when transaction amount equals the Rs 50 lakh threshold")
    void testCtr_aboveThreshold_raisesReview() {
        String accountNumber = "1111222233334444";
        TransactionLog txn = buildTxn(accountNumber, "9999888877776666", new BigDecimal("5000000"), LocalDateTime.now());
        FraudRule ctrRule = buildRule(RuleType.CTR, new BigDecimal("5000000"), RuleAction.REVIEW);

        stubOnlyActiveRule(RuleType.CTR, ctrRule);

        Optional<FraudRuleEngine.RuleEvaluationResult> result = fraudRuleEngine.evaluate(txn);

        assertThat(result).isPresent();
        assertThat(result.get().ruleTriggered()).isEqualTo("CTR_THRESHOLD");
        assertThat(result.get().riskScore()).isEqualTo(90);
        assertThat(result.get().action()).isEqualTo("REVIEW");
    }

    @Test
    @DisplayName("CTR: should not raise an alert when transaction amount is below the Rs 50 lakh threshold")
    void testCtr_belowThreshold_noAlert() {
        String accountNumber = "1111222233334444";
        TransactionLog txn = buildTxn(accountNumber, "9999888877776666", new BigDecimal("4999999"), LocalDateTime.now());
        FraudRule ctrRule = buildRule(RuleType.CTR, new BigDecimal("5000000"), RuleAction.REVIEW);

        stubOnlyActiveRule(RuleType.CTR, ctrRule);

        Optional<FraudRuleEngine.RuleEvaluationResult> result = fraudRuleEngine.evaluate(txn);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Structuring: should raise a BLOCK alert when 3 transactions in the 85-100 percent CTR band occur within 24 hours")
    void testStructuring_threeTransactions_raisesBlock() {
        String accountNumber = "1111222233334444";
        LocalDateTime now = LocalDateTime.now();
        TransactionLog txn = buildTxn(accountNumber, "9999888877776666", new BigDecimal("4500000"), now);
        FraudRule structuringRule = buildRule(RuleType.STRUCTURING, new BigDecimal("5000000"), RuleAction.BLOCK);

        stubOnlyActiveRule(RuleType.STRUCTURING, structuringRule);
        when(transactionLogRepository.findByAccountNumberAndOccurredAtAfter(eq(accountNumber), any()))
                .thenReturn(List.of(
                        buildTxn(accountNumber, "9999888877776666", new BigDecimal("4500000"), now.minusHours(1)),
                        buildTxn(accountNumber, "9999888877776666", new BigDecimal("4600000"), now.minusHours(3)),
                        buildTxn(accountNumber, "9999888877776666", new BigDecimal("4700000"), now.minusHours(6))
                ));

        Optional<FraudRuleEngine.RuleEvaluationResult> result = fraudRuleEngine.evaluate(txn);

        assertThat(result).isPresent();
        assertThat(result.get().ruleTriggered()).isEqualTo("STRUCTURING");
        assertThat(result.get().riskScore()).isEqualTo(85);
        assertThat(result.get().action()).isEqualTo("BLOCK");
    }

    @Test
    @DisplayName("Structuring: should not raise an alert when only 2 transactions fall in the CTR band within 24 hours")
    void testStructuring_twoTransactions_noAlert() {
        String accountNumber = "1111222233334444";
        LocalDateTime now = LocalDateTime.now();
        TransactionLog txn = buildTxn(accountNumber, "9999888877776666", new BigDecimal("4500000"), now);
        FraudRule structuringRule = buildRule(RuleType.STRUCTURING, new BigDecimal("5000000"), RuleAction.BLOCK);

        stubOnlyActiveRule(RuleType.STRUCTURING, structuringRule);
        when(transactionLogRepository.findByAccountNumberAndOccurredAtAfter(eq(accountNumber), any()))
                .thenReturn(List.of(
                        buildTxn(accountNumber, "9999888877776666", new BigDecimal("4500000"), now.minusHours(1)),
                        buildTxn(accountNumber, "9999888877776666", new BigDecimal("4600000"), now.minusHours(3))
                ));

        Optional<FraudRuleEngine.RuleEvaluationResult> result = fraudRuleEngine.evaluate(txn);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Structuring: should not raise an alert when transactions are below the 85 percent floor")
    void testStructuring_belowFloor_noAlert() {
        String accountNumber = "1111222233334444";
        LocalDateTime now = LocalDateTime.now();
        TransactionLog txn = buildTxn(accountNumber, "9999888877776666", new BigDecimal("4000000"), now);
        FraudRule structuringRule = buildRule(RuleType.STRUCTURING, new BigDecimal("5000000"), RuleAction.BLOCK);

        stubOnlyActiveRule(RuleType.STRUCTURING, structuringRule);
        when(transactionLogRepository.findByAccountNumberAndOccurredAtAfter(eq(accountNumber), any()))
                .thenReturn(List.of(
                        buildTxn(accountNumber, "9999888877776666", new BigDecimal("4000000"), now.minusHours(1)),
                        buildTxn(accountNumber, "9999888877776666", new BigDecimal("4000000"), now.minusHours(3)),
                        buildTxn(accountNumber, "9999888877776666", new BigDecimal("4000000"), now.minusHours(6))
                ));

        Optional<FraudRuleEngine.RuleEvaluationResult> result = fraudRuleEngine.evaluate(txn);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should raise a daily-limit-breach alert when today's cumulative transfers exceed the threshold")
    void testDailyLimitBreach_exceeded_raisesAlert() {
        // Given
        String accountNumber = "1111222233334444";
        LocalDateTime now = LocalDateTime.now();
        TransactionLog txn = buildTxn(accountNumber, "9999888877776666", new BigDecimal("60000"), now);
        FraudRule dailyLimitRule = buildRule(RuleType.DAILY_LIMIT_BREACH, new BigDecimal("100000"), RuleAction.BLOCK);

        stubOnlyActiveRule(RuleType.DAILY_LIMIT_BREACH, dailyLimitRule);
        when(transactionLogRepository.findByAccountNumberAndOccurredAtAfter(eq(accountNumber), any()))
                .thenReturn(List.of(
                        buildTxn(accountNumber, "9999888877776666", new BigDecimal("60000"), now),
                        buildTxn(accountNumber, "9999888877776666", new BigDecimal("60000"), now)
                ));

        // When
        Optional<FraudRuleEngine.RuleEvaluationResult> result = fraudRuleEngine.evaluate(txn);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().ruleTriggered()).isEqualTo("DAILY_LIMIT_BREACH");
        assertThat(result.get().action()).isEqualTo("BLOCK");
    }
}
