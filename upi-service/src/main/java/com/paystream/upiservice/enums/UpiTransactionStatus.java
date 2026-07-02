package com.paystream.upiservice.enums;

public enum UpiTransactionStatus {
    INITIATED,
    PENDING_PIN,
    PIN_VERIFIED,
    PROCESSING,
    COMPLETED,
    FAILED,
    EXPIRED,
    DECLINED,
    RECONCILIATION_REQUIRED
}
