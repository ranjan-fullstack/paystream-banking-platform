package com.paystream.impsservice.service;

import com.paystream.impsservice.dto.MmidRegisterRequest;
import com.paystream.impsservice.dto.MmidResponse;

public interface MmidService {
    MmidResponse register(MmidRegisterRequest request);
    MmidResponse getByMobile(String mobile);
}
