package com.paystream.upiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;

@Getter
@Builder
@AllArgsConstructor
public class VpaResponse implements Serializable {
    private String vpa;
    private String customerId;
    private String accountNumber;
    private boolean isDefault;
    private boolean active;
}
