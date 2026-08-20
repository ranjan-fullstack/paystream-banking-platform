package com.paystream.frauddetectionservice.config;

import com.paystream.frauddetectionservice.entity.FraudRule;
import com.paystream.frauddetectionservice.enums.RuleAction;
import com.paystream.frauddetectionservice.enums.RuleType;
import com.paystream.frauddetectionservice.repository.FraudRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Seeds default fraud rule thresholds on first boot. Admins can tune them
 * afterwards via PUT /api/v1/fraud/rules/{id}.
 */
@Component
@RequiredArgsConstructor
public class FraudRuleSeeder implements CommandLineRunner {

    private final FraudRuleRepository fraudRuleRepository;

    @Override
    public void run(String... args) {
        seed(RuleType.VELOCITY, "More than 5 transactions in 10 minutes", new BigDecimal("5"), RuleAction.ALERT);
        seed(RuleType.AMOUNT_ANOMALY, "Amount exceeds 3x account average", new BigDecimal("3"), RuleAction.ALERT);
        seed(RuleType.ODD_HOURS, "RTGS/NEFT outside business hours", BigDecimal.ZERO, RuleAction.BLOCK);
        seed(RuleType.DUPLICATE_TRANSACTION, "Same amount/beneficiary within 5 minutes", new BigDecimal("5"), RuleAction.BLOCK);
        seed(RuleType.HIGH_RISK_ACCOUNT, "Sender or receiver flagged high risk", BigDecimal.ZERO, RuleAction.ALERT);
        seed(RuleType.DAILY_LIMIT_BREACH, "Daily cumulative amount exceeds limit", new BigDecimal("1000000"), RuleAction.BLOCK);
        seed(RuleType.CTR, "Cash Transaction Report — single transfer Rs 10 lakh or above", new BigDecimal("1000000"), RuleAction.REVIEW);
        seed(RuleType.STRUCTURING, "Structuring — multiple transfers near CTR threshold within 24 hours", new BigDecimal("1000000"), RuleAction.BLOCK);
    }

    private void seed(RuleType type, String name, BigDecimal threshold, RuleAction action) {
        if (fraudRuleRepository.existsByRuleType(type)) {
            return;
        }
        FraudRule rule = new FraudRule();
        rule.setRuleName(name);
        rule.setRuleType(type);
        rule.setThreshold(threshold);
        rule.setAction(action);
        rule.setActive(true);
        fraudRuleRepository.save(rule);
    }
}
