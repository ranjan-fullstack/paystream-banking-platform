package com.paystream.customerservice.dto;

import com.paystream.customerservice.enums.KycStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KycVerifyRequest {

    @NotNull
    private KycStatus status; // VERIFIED or REJECTED

    private String remarks;
}
