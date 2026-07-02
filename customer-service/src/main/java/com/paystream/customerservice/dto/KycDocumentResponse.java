package com.paystream.customerservice.dto;

import com.paystream.customerservice.enums.DocumentStatus;
import com.paystream.customerservice.enums.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class KycDocumentResponse {
    private UUID id;
    private DocumentType documentType;
    private DocumentStatus status;
    private LocalDateTime verifiedAt;
}
