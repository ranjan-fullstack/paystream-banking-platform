package com.paystream.neftservice.service;

import com.paystream.neftservice.dto.NeftBatchResponse;
import com.paystream.neftservice.dto.NeftTransactionResponse;
import com.paystream.neftservice.dto.NeftTransferRequest;

import java.util.List;

public interface NeftService {
    NeftTransactionResponse initiateTransfer(NeftTransferRequest request);
    NeftTransactionResponse trackStatus(String referenceNumber);
    List<NeftTransactionResponse> getHistory(String customerId);
    List<NeftBatchResponse> getBatches();
    NeftBatchResponse getBatchDetails(String batchNumber);
}
