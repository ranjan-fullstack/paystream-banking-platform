package com.paystream.impsservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class MmidResponse {
    private String accountNumber;
    private String mobileNumber;
    private String mmid;
    private boolean active;
}
