package com.paystream.customerservice.dto;

import com.paystream.customerservice.enums.KycStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class KycStatusResponse {
    private String customerId;
    private KycStatus kycStatus;
    private List<KycDocumentResponse> documents;
}
