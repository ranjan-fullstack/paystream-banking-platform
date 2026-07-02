package com.paystream.upiservice.service;

import com.paystream.upiservice.dto.*;

import java.util.List;

public interface UpiTransactionService {
    UpiTransactionResponse pay(UpiPayRequest request);
    UpiTransactionResponse collect(UpiCollectMoneyRequest request);
    UpiTransactionResponse respondToCollect(String upiTransactionId, CollectRespondRequest request);
    UpiTransactionResponse refund(RefundRequest request);
    UpiTransactionResponse getStatus(String upiTransactionId);
    List<UpiTransactionResponse> getHistory(String vpa);
}
