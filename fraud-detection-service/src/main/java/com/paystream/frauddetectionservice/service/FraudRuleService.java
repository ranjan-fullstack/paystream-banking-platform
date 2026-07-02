package com.paystream.frauddetectionservice.service;

import com.paystream.frauddetectionservice.dto.FraudRuleResponse;
import com.paystream.frauddetectionservice.dto.FraudRuleUpdateRequest;

import java.util.List;
import java.util.UUID;

public interface FraudRuleService {
    List<FraudRuleResponse> getAll();
    FraudRuleResponse update(UUID id, FraudRuleUpdateRequest request);
}
