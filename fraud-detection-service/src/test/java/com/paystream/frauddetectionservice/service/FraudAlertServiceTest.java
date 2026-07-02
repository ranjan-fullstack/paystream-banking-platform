package com.paystream.frauddetectionservice.service;

import com.paystream.frauddetectionservice.dto.AlertReviewRequest;
import com.paystream.frauddetectionservice.dto.FraudAlertResponse;
import com.paystream.frauddetectionservice.entity.FraudAlert;
import com.paystream.frauddetectionservice.enums.AlertStatus;
import com.paystream.frauddetectionservice.enums.RuleAction;
import com.paystream.frauddetectionservice.exception.FraudAlertNotFoundException;
import com.paystream.frauddetectionservice.repository.FraudAlertRepository;
import com.paystream.frauddetectionservice.service.impl.FraudAlertServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FraudAlertService unit tests")
class FraudAlertServiceTest {

    @Mock
    private FraudAlertRepository fraudAlertRepository;

    @InjectMocks
    private FraudAlertServiceImpl fraudAlertService;

    @Test
    @DisplayName("Should review and close a fraud alert successfully")
    void testReviewAlert_Success() {
        // Given
        UUID alertId = UUID.randomUUID();
        FraudAlert alert = new FraudAlert();
        alert.setId(alertId);
        alert.setAccountNumber("1111222233334444");
        alert.setTransactionReference("IMPS202606290001");
        alert.setRuleTriggered("VELOCITY_CHECK");
        alert.setRiskScore(60);
        alert.setAction(RuleAction.ALERT);
        alert.setStatus(AlertStatus.OPEN);

        AlertReviewRequest request = new AlertReviewRequest();
        request.setStatus(AlertStatus.CLOSED);
        request.setRemarks("Confirmed legitimate after customer callback");

        when(fraudAlertRepository.findById(alertId)).thenReturn(Optional.of(alert));
        when(fraudAlertRepository.save(any(FraudAlert.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        FraudAlertResponse response = fraudAlertService.review(alertId, request);

        // Then
        assertThat(response.getStatus()).isEqualTo(AlertStatus.CLOSED);
        assertThat(response.getReviewRemarks()).isEqualTo("Confirmed legitimate after customer callback");
    }

    @Test
    @DisplayName("Should throw exception when reviewing a fraud alert that does not exist")
    void testReviewAlert_NotFound_throwsException() {
        // Given
        UUID alertId = UUID.randomUUID();
        AlertReviewRequest request = new AlertReviewRequest();
        request.setStatus(AlertStatus.CLOSED);

        when(fraudAlertRepository.findById(alertId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> fraudAlertService.review(alertId, request))
                .isInstanceOf(FraudAlertNotFoundException.class);
    }
}
