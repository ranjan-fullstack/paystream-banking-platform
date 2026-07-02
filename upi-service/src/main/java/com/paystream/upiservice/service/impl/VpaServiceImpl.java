package com.paystream.upiservice.service.impl;

import com.paystream.upiservice.config.RedisCacheConfig;
import com.paystream.upiservice.dto.SetPinRequest;
import com.paystream.upiservice.dto.VpaRegisterRequest;
import com.paystream.upiservice.dto.VpaResponse;
import com.paystream.upiservice.entity.VirtualPaymentAddress;
import com.paystream.upiservice.exception.VpaNotFoundException;
import com.paystream.upiservice.repository.VpaRepository;
import com.paystream.upiservice.service.VpaService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VpaServiceImpl implements VpaService {

    private static final String BANK_HANDLE = "@paystream";
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final VpaRepository vpaRepository;

    @Override
    @Transactional
    public VpaResponse register(VpaRegisterRequest request) {
        String vpa = request.getHandle() + BANK_HANDLE;
        if (vpaRepository.existsByVpa(vpa)) {
            throw new IllegalArgumentException("VPA already taken: " + vpa);
        }

        VirtualPaymentAddress entity = new VirtualPaymentAddress();
        entity.setVpa(vpa);
        entity.setCustomerId(request.getCustomerId());
        entity.setAccountNumber(request.getAccountNumber());
        entity.setDefault(vpaRepository.findByCustomerId(request.getCustomerId()).isEmpty());
        entity.setActive(true);

        return toResponse(vpaRepository.save(entity));
    }

    @Override
    @Cacheable(value = RedisCacheConfig.VPA_CACHE, key = "#vpa")
    public VpaResponse getByVpa(String vpa) {
        return toResponse(findOrThrow(vpa));
    }

    @Override
    public List<VpaResponse> getByCustomerId(String customerId) {
        return vpaRepository.findByCustomerId(customerId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = RedisCacheConfig.VPA_CACHE, key = "#request.vpa")
    public void setPin(SetPinRequest request) {
        VirtualPaymentAddress entity = findOrThrow(request.getVpa());
        entity.setUpiPin(PASSWORD_ENCODER.encode(request.getUpiPin()));
        vpaRepository.save(entity);
    }

    private VirtualPaymentAddress findOrThrow(String vpa) {
        return vpaRepository.findByVpa(vpa).orElseThrow(() -> new VpaNotFoundException(vpa));
    }

    private VpaResponse toResponse(VirtualPaymentAddress entity) {
        return VpaResponse.builder()
                .vpa(entity.getVpa())
                .customerId(entity.getCustomerId())
                .accountNumber(entity.getAccountNumber())
                .isDefault(entity.isDefault())
                .active(entity.isActive())
                .build();
    }
}
