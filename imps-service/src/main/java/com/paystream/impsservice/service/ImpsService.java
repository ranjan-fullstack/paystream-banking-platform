package com.paystream.impsservice.service;

import com.paystream.impsservice.dto.ImpsTransactionResponse;
import com.paystream.impsservice.dto.ImpsTransferRequest;

import java.util.List;

public interface ImpsService {
    ImpsTransactionResponse transfer(ImpsTransferRequest request);
    ImpsTransactionResponse trackStatus(String referenceNumber);
    List<ImpsTransactionResponse> getHistory(String customerId);
}
