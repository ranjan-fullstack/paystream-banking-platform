package com.paystream.frauddetectionservice.service.impl;

import com.paystream.frauddetectionservice.dto.AlertReviewRequest;
import com.paystream.frauddetectionservice.dto.FraudAlertResponse;
import com.paystream.frauddetectionservice.entity.FraudAlert;
import com.paystream.frauddetectionservice.exception.FraudAlertNotFoundException;
import com.paystream.frauddetectionservice.repository.FraudAlertRepository;
import com.paystream.frauddetectionservice.service.FraudAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FraudAlertServiceImpl implements FraudAlertService {

    private final FraudAlertRepository fraudAlertRepository;

    @Override
    public List<FraudAlertResponse> getAll() {
        return fraudAlertRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public FraudAlertResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Override
    @Transactional
    public FraudAlertResponse review(UUID id, AlertReviewRequest request) {
        FraudAlert alert = findOrThrow(id);
        alert.setStatus(request.getStatus());
        alert.setReviewRemarks(request.getRemarks());
        return toResponse(fraudAlertRepository.save(alert));
    }

    private FraudAlert findOrThrow(UUID id) {
        return fraudAlertRepository.findById(id)
                .orElseThrow(() -> new FraudAlertNotFoundException(id.toString()));
    }

    private FraudAlertResponse toResponse(FraudAlert alert) {
        return FraudAlertResponse.builder()
                .id(alert.getId())
                .accountNumber(alert.getAccountNumber())
                .transactionReference(alert.getTransactionReference())
                .ruleTriggered(alert.getRuleTriggered())
                .riskScore(alert.getRiskScore())
                .action(alert.getAction())
                .status(alert.getStatus())
                .reviewRemarks(alert.getReviewRemarks())
                .createdAt(alert.getCreatedAt())
                .build();
    }
}
