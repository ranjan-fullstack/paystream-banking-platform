package com.paystream.frauddetectionservice.dto;

import com.paystream.frauddetectionservice.enums.AlertStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AlertReviewRequest {

    @NotNull
    private AlertStatus status; // REVIEWED or CLOSED

    private String remarks;
}
