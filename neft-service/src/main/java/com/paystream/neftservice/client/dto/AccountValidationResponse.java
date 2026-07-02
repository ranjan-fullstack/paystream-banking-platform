package com.paystream.neftservice.client.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountValidationResponse {
    private boolean valid;
    private String accountNumber;
    private String status;
    private String reason;
}
