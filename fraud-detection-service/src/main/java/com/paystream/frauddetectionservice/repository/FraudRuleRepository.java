package com.paystream.frauddetectionservice.repository;

import com.paystream.frauddetectionservice.entity.FraudRule;
import com.paystream.frauddetectionservice.enums.RuleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FraudRuleRepository extends JpaRepository<FraudRule, UUID> {
    Optional<FraudRule> findByRuleType(RuleType ruleType);
    boolean existsByRuleType(RuleType ruleType);
}
