package com.paystream.impsservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MmidRegisterRequest {

    @NotBlank
    private String customerId;

    @NotBlank
    private String accountNumber;

    @NotBlank
    @Pattern(regexp = "[6-9]\\d{9}", message = "mobileNumber must be a valid 10-digit Indian mobile number")
    private String mobileNumber;
}
