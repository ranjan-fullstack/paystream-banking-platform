package com.paystream.customerservice.service.impl;

import com.paystream.customerservice.dto.*;
import com.paystream.customerservice.entity.Customer;
import com.paystream.customerservice.entity.KycDocument;
import com.paystream.customerservice.enums.DocumentStatus;
import com.paystream.customerservice.enums.KycStatus;
import com.paystream.customerservice.exception.CustomerNotFoundException;
import com.paystream.customerservice.exception.DuplicateCustomerException;
import com.paystream.customerservice.repository.CustomerRepository;
import com.paystream.customerservice.repository.KycDocumentRepository;
import com.paystream.customerservice.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_CIF_RETRIES = 5;

    private final CustomerRepository customerRepository;
    private final KycDocumentRepository kycDocumentRepository;

    @Override
    @Transactional
    public CustomerResponse register(CustomerRegisterRequest request) {
        if (customerRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateCustomerException("A customer with this email already exists");
        }
        if (customerRepository.existsByMobile(request.getMobile())) {
            throw new DuplicateCustomerException("A customer with this mobile number already exists");
        }

        Customer customer = new Customer();
        customer.setCustomerId(generateUniqueCif());
        customer.setFirstName(request.getFirstName());
        customer.setLastName(request.getLastName());
        customer.setEmail(request.getEmail());
        customer.setMobile(request.getMobile());
        customer.setPanNumber(request.getPanNumber());
        customer.setAadhaarNumber(request.getAadhaarNumber());
        customer.setDateOfBirth(request.getDateOfBirth());
        customer.setUserId(request.getUserId());
        customer.setKycStatus(KycStatus.PENDING);

        customer = customerRepository.save(customer);
        return toResponse(customer);
    }

    @Override
    public CustomerResponse getByCustomerId(String customerId) {
        return toResponse(findCustomerOrThrow(customerId));
    }

    @Override
    @Transactional
    public CustomerResponse update(String customerId, CustomerUpdateRequest request) {
        Customer customer = findCustomerOrThrow(customerId);

        if (request.getFirstName() != null) customer.setFirstName(request.getFirstName());
        if (request.getLastName() != null) customer.setLastName(request.getLastName());
        if (request.getEmail() != null) customer.setEmail(request.getEmail());
        if (request.getMobile() != null) customer.setMobile(request.getMobile());

        return toResponse(customerRepository.save(customer));
    }

    @Override
    @Transactional
    public KycStatusResponse submitKyc(String customerId, KycSubmitRequest request) {
        Customer customer = findCustomerOrThrow(customerId);

        List<KycDocument> documents = request.getDocuments().stream().map(docReq -> {
            KycDocument doc = new KycDocument();
            doc.setCustomer(customer);
            doc.setDocumentType(docReq.getDocumentType());
            doc.setDocumentNumber(docReq.getDocumentNumber());
            doc.setStatus(DocumentStatus.PENDING);
            return doc;
        }).collect(Collectors.toList());

        kycDocumentRepository.saveAll(documents);

        customer.setKycStatus(KycStatus.IN_PROGRESS);
        customerRepository.save(customer);

        return getKycStatus(customerId);
    }

    @Override
    public KycStatusResponse getKycStatus(String customerId) {
        Customer customer = findCustomerOrThrow(customerId);
        List<KycDocumentResponse> documents = kycDocumentRepository.findByCustomerId(customer.getId())
                .stream()
                .map(this::toDocumentResponse)
                .collect(Collectors.toList());

        return KycStatusResponse.builder()
                .customerId(customer.getCustomerId())
                .kycStatus(customer.getKycStatus())
                .documents(documents)
                .build();
    }

    @Override
    @Transactional
    public KycStatusResponse verifyKyc(String customerId, KycVerifyRequest request) {
        Customer customer = findCustomerOrThrow(customerId);

        DocumentStatus docStatus = request.getStatus() == KycStatus.VERIFIED
                ? DocumentStatus.VERIFIED
                : DocumentStatus.REJECTED;

        List<KycDocument> documents = kycDocumentRepository.findByCustomerId(customer.getId());
        for (KycDocument doc : documents) {
            if (doc.getStatus() == DocumentStatus.PENDING) {
                doc.setStatus(docStatus);
                doc.setVerifiedAt(LocalDateTime.now());
            }
        }
        kycDocumentRepository.saveAll(documents);

        customer.setKycStatus(request.getStatus());
        customerRepository.save(customer);

        return getKycStatus(customerId);
    }

    private Customer findCustomerOrThrow(String customerId) {
        return customerRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }

    private String generateUniqueCif() {
        for (int attempt = 0; attempt < MAX_CIF_RETRIES; attempt++) {
            String candidate = String.format("CIF%06d", RANDOM.nextInt(1_000_000));
            if (!customerRepository.existsByCustomerId(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique CIF number, please retry");
    }

    private CustomerResponse toResponse(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .customerId(customer.getCustomerId())
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .email(customer.getEmail())
                .mobile(customer.getMobile())
                .kycStatus(customer.getKycStatus())
                .riskRating(customer.getRiskRating())
                .createdAt(customer.getCreatedAt())
                .build();
    }

    private KycDocumentResponse toDocumentResponse(KycDocument doc) {
        return KycDocumentResponse.builder()
                .id(doc.getId())
                .documentType(doc.getDocumentType())
                .status(doc.getStatus())
                .verifiedAt(doc.getVerifiedAt())
                .build();
    }
}
