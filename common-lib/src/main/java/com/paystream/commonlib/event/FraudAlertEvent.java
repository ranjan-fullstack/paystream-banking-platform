package com.paystream.commonlib.event;

import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudAlertEvent {

    private String accountNumber;
    private String transactionReference;
    private String ruleTriggered;
    private Integer riskScore;
    private String action; // ALERT, BLOCK
    private Instant createdAt;
}
