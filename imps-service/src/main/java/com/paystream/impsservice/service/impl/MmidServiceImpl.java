package com.paystream.impsservice.service.impl;

import com.paystream.impsservice.dto.MmidRegisterRequest;
import com.paystream.impsservice.dto.MmidResponse;
import com.paystream.impsservice.entity.MmidRegistration;
import com.paystream.impsservice.exception.MmidNotFoundException;
import com.paystream.impsservice.repository.MmidRegistrationRepository;
import com.paystream.impsservice.service.MmidService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class MmidServiceImpl implements MmidService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final MmidRegistrationRepository mmidRegistrationRepository;

    @Override
    @Transactional
    public MmidResponse register(MmidRegisterRequest request) {
        MmidRegistration registration = mmidRegistrationRepository.findByMobileNumber(request.getMobileNumber())
                .orElseGet(MmidRegistration::new);

        registration.setCustomerId(request.getCustomerId());
        registration.setAccountNumber(request.getAccountNumber());
        registration.setMobileNumber(request.getMobileNumber());
        registration.setActive(true);
        if (registration.getMmid() == null) {
            registration.setMmid(generateUniqueMmid());
        }

        return toResponse(mmidRegistrationRepository.save(registration));
    }

    @Override
    public MmidResponse getByMobile(String mobile) {
        return mmidRegistrationRepository.findByMobileNumber(mobile)
                .map(this::toResponse)
                .orElseThrow(() -> new MmidNotFoundException(mobile));
    }

    private String generateUniqueMmid() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = String.format("%07d", RANDOM.nextInt(10_000_000));
            if (!mmidRegistrationRepository.existsByMmid(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique MMID, please retry");
    }

    private MmidResponse toResponse(MmidRegistration registration) {
        return MmidResponse.builder()
                .accountNumber(registration.getAccountNumber())
                .mobileNumber(registration.getMobileNumber())
                .mmid(registration.getMmid())
                .active(registration.isActive())
                .build();
    }
}
