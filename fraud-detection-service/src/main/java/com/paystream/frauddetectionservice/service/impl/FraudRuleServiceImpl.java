package com.paystream.frauddetectionservice.service.impl;

import com.paystream.frauddetectionservice.dto.FraudRuleResponse;
import com.paystream.frauddetectionservice.dto.FraudRuleUpdateRequest;
import com.paystream.frauddetectionservice.entity.FraudRule;
import com.paystream.frauddetectionservice.exception.FraudRuleNotFoundException;
import com.paystream.frauddetectionservice.repository.FraudRuleRepository;
import com.paystream.frauddetectionservice.service.FraudRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FraudRuleServiceImpl implements FraudRuleService {

    private final FraudRuleRepository fraudRuleRepository;

    @Override
    public List<FraudRuleResponse> getAll() {
        return fraudRuleRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public FraudRuleResponse update(UUID id, FraudRuleUpdateRequest request) {
        FraudRule rule = fraudRuleRepository.findById(id)
                .orElseThrow(() -> new FraudRuleNotFoundException(id.toString()));
        rule.setThreshold(request.getThreshold());
        rule.setAction(request.getAction());
        rule.setActive(request.getActive());
        return toResponse(fraudRuleRepository.save(rule));
    }

    private FraudRuleResponse toResponse(FraudRule rule) {
        return FraudRuleResponse.builder()
                .id(rule.getId())
                .ruleName(rule.getRuleName())
                .ruleType(rule.getRuleType())
                .threshold(rule.getThreshold())
                .action(rule.getAction())
                .active(rule.isActive())
                .build();
    }
}
