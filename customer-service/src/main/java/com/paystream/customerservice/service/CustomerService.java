package com.paystream.customerservice.service;

import com.paystream.customerservice.dto.*;

public interface CustomerService {
    CustomerResponse register(CustomerRegisterRequest request);
    CustomerResponse getByCustomerId(String customerId);
    CustomerResponse update(String customerId, CustomerUpdateRequest request);
    KycStatusResponse submitKyc(String customerId, KycSubmitRequest request);
    KycStatusResponse getKycStatus(String customerId);
    KycStatusResponse verifyKyc(String customerId, KycVerifyRequest request);
}
