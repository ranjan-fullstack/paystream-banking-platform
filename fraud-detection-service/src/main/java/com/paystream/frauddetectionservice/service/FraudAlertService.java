package com.paystream.frauddetectionservice.service;

import com.paystream.frauddetectionservice.dto.AlertReviewRequest;
import com.paystream.frauddetectionservice.dto.FraudAlertResponse;

import java.util.List;
import java.util.UUID;

public interface FraudAlertService {
    List<FraudAlertResponse> getAll();
    FraudAlertResponse getById(UUID id);
    FraudAlertResponse review(UUID id, AlertReviewRequest request);
}
