package com.paystream.frauddetectionservice.service;

import com.paystream.frauddetectionservice.dto.FraudRuleResponse;
import com.paystream.frauddetectionservice.dto.FraudRuleUpdateRequest;
import com.paystream.frauddetectionservice.entity.FraudRule;
import com.paystream.frauddetectionservice.enums.RuleAction;
import com.paystream.frauddetectionservice.enums.RuleType;
import com.paystream.frauddetectionservice.exception.FraudRuleNotFoundException;
import com.paystream.frauddetectionservice.repository.FraudRuleRepository;
import com.paystream.frauddetectionservice.service.impl.FraudRuleServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FraudRuleService unit tests")
class FraudRuleServiceTest {

    @Mock
    private FraudRuleRepository fraudRuleRepository;

    @InjectMocks
    private FraudRuleServiceImpl fraudRuleService;

    @Test
    @DisplayName("Should update a fraud rule's threshold, action and active flag successfully")
    void testUpdateRule_Success() {
        // Given
        UUID ruleId = UUID.randomUUID();
        FraudRule rule = new FraudRule();
        rule.setId(ruleId);
        rule.setRuleName("VELOCITY_RULE");
        rule.setRuleType(RuleType.VELOCITY);
        rule.setThreshold(new BigDecimal("5"));
        rule.setAction(RuleAction.ALERT);
        rule.setActive(true);

        FraudRuleUpdateRequest request = new FraudRuleUpdateRequest();
        request.setThreshold(new BigDecimal("8"));
        request.setAction(RuleAction.BLOCK);
        request.setActive(false);

        when(fraudRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule));
        when(fraudRuleRepository.save(any(FraudRule.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        FraudRuleResponse response = fraudRuleService.update(ruleId, request);

        // Then
        assertThat(response.getThreshold()).isEqualByComparingTo("8");
        assertThat(response.getAction()).isEqualTo(RuleAction.BLOCK);
        assertThat(response.isActive()).isFalse();
    }

    @Test
    @DisplayName("Should throw exception when updating a fraud rule that does not exist")
    void testUpdateRule_NotFound_throwsException() {
        // Given
        UUID ruleId = UUID.randomUUID();
        FraudRuleUpdateRequest request = new FraudRuleUpdateRequest();
        request.setThreshold(new BigDecimal("8"));
        request.setAction(RuleAction.BLOCK);
        request.setActive(false);

        when(fraudRuleRepository.findById(ruleId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> fraudRuleService.update(ruleId, request))
                .isInstanceOf(FraudRuleNotFoundException.class);
    }
}
