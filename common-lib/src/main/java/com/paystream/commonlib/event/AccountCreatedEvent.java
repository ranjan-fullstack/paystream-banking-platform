package com.paystream.commonlib.event;

import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountCreatedEvent {

    private String accountNumber;
    private String customerId;
    private String accountType; // SAVINGS, CURRENT, SALARY, NRI
    private Instant createdAt;
}
