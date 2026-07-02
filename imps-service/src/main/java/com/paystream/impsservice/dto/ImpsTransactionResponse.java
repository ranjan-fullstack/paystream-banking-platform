package com.paystream.impsservice.dto;

import com.paystream.impsservice.enums.ImpsStatus;
import com.paystream.impsservice.enums.TransferMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class ImpsTransactionResponse {
    private String impsReferenceNumber;
    private TransferMode transferMode;
    private String senderAccountNumber;
    private String beneficiaryName;
    private BigDecimal amount;
    private ImpsStatus status;
    private String rrn;
    private LocalDateTime initiatedAt;
    private LocalDateTime completedAt;
    private String failureReason;
}
