package com.paystream.rtgsservice.dto;

import com.paystream.rtgsservice.enums.RtgsPurpose;
import com.paystream.rtgsservice.enums.RtgsStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class RtgsTransactionResponse {
    private String rtgsReferenceNumber;
    private String senderAccountNumber;
    private String beneficiaryAccountNumber;
    private String beneficiaryName;
    private BigDecimal amount;
    private RtgsPurpose purpose;
    private RtgsStatus status;
    private LocalDateTime initiatedAt;
    private LocalDateTime settledAt;
    private String rbiUtrNumber;
    private String failureReason;
}
