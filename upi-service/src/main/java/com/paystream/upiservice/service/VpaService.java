package com.paystream.upiservice.service;

import com.paystream.upiservice.dto.SetPinRequest;
import com.paystream.upiservice.dto.VpaRegisterRequest;
import com.paystream.upiservice.dto.VpaResponse;

import java.util.List;

public interface VpaService {
    VpaResponse register(VpaRegisterRequest request);
    VpaResponse getByVpa(String vpa);
    List<VpaResponse> getByCustomerId(String customerId);
    void setPin(SetPinRequest request);
}
