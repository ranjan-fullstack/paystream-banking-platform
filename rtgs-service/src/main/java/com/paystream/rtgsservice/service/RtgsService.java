package com.paystream.rtgsservice.service;

import com.paystream.rtgsservice.dto.RtgsTransactionResponse;
import com.paystream.rtgsservice.dto.RtgsTransferRequest;

import java.util.List;

public interface RtgsService {
    RtgsTransactionResponse initiateTransfer(RtgsTransferRequest request);
    RtgsTransactionResponse trackStatus(String referenceNumber);
    List<RtgsTransactionResponse> getHistory(String customerId);
    RtgsTransactionResponse recall(String referenceNumber);
}
