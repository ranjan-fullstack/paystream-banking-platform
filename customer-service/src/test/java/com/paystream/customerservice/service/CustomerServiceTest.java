package com.paystream.customerservice.service;

import com.paystream.customerservice.dto.*;
import com.paystream.customerservice.entity.Customer;
import com.paystream.customerservice.entity.KycDocument;
import com.paystream.customerservice.enums.DocumentStatus;
import com.paystream.customerservice.enums.DocumentType;
import com.paystream.customerservice.enums.KycStatus;
import com.paystream.customerservice.exception.CustomerNotFoundException;
import com.paystream.customerservice.exception.DuplicateCustomerException;
import com.paystream.customerservice.repository.CustomerRepository;
import com.paystream.customerservice.repository.KycDocumentRepository;
import com.paystream.customerservice.service.impl.CustomerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerService unit tests")
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private KycDocumentRepository kycDocumentRepository;

    @InjectMocks
    private CustomerServiceImpl customerService;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setCustomerId("CIF001234");
        customer.setFirstName("Lakshmi");
        customer.setLastName("Iyer");
        customer.setEmail("lakshmi.iyer@example.com");
        customer.setMobile("9876543210");
        customer.setPanNumber("ABCDE1234F");
        customer.setAadhaarNumber("123456789012");
        customer.setDateOfBirth(LocalDate.of(1990, 5, 12));
        customer.setUserId(101L);
        customer.setKycStatus(KycStatus.PENDING);
    }

    private CustomerRegisterRequest buildRegisterRequest() {
        CustomerRegisterRequest request = new CustomerRegisterRequest();
        request.setFirstName("Lakshmi");
        request.setLastName("Iyer");
        request.setEmail("lakshmi.iyer@example.com");
        request.setMobile("9876543210");
        request.setPanNumber("ABCDE1234F");
        request.setAadhaarNumber("123456789012");
        request.setDateOfBirth(LocalDate.of(1990, 5, 12));
        request.setUserId(101L);
        return request;
    }

    @Test
    @DisplayName("Should register a new customer successfully with a generated CIF number")
    void testRegisterCustomerSuccess() {
        // Given
        CustomerRegisterRequest request = buildRegisterRequest();
        when(customerRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(customerRepository.existsByMobile(request.getMobile())).thenReturn(false);
        when(customerRepository.existsByCustomerId(anyString())).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        CustomerResponse response = customerService.register(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getCustomerId()).startsWith("CIF");
        assertThat(response.getEmail()).isEqualTo("lakshmi.iyer@example.com");
        assertThat(response.getKycStatus()).isEqualTo(KycStatus.PENDING);
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    @DisplayName("Should throw exception when registering with a duplicate email")
    void testRegisterDuplicateEmail_throwsException() {
        // Given
        CustomerRegisterRequest request = buildRegisterRequest();
        when(customerRepository.existsByEmail(request.getEmail())).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> customerService.register(request))
                .isInstanceOf(DuplicateCustomerException.class)
                .hasMessageContaining("email");

        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should fetch customer by customer ID successfully")
    void testGetCustomerByIdSuccess() {
        // Given
        when(customerRepository.findByCustomerId("CIF001234")).thenReturn(Optional.of(customer));

        // When
        CustomerResponse response = customerService.getByCustomerId("CIF001234");

        // Then
        assertThat(response.getCustomerId()).isEqualTo("CIF001234");
        assertThat(response.getFirstName()).isEqualTo("Lakshmi");
    }

    @Test
    @DisplayName("Should throw exception when customer is not found by customer ID")
    void testGetCustomerNotFound_throwsException() {
        // Given
        when(customerRepository.findByCustomerId("CIF999999")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> customerService.getByCustomerId("CIF999999"))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessageContaining("CIF999999");
    }

    @Test
    @DisplayName("Should submit KYC documents successfully and move status to IN_PROGRESS")
    void testSubmitKycSuccess() {
        // Given
        KycDocumentRequest docRequest = new KycDocumentRequest();
        docRequest.setDocumentType(DocumentType.PAN);
        docRequest.setDocumentNumber("ABCDE1234F");

        KycSubmitRequest request = new KycSubmitRequest();
        request.setDocuments(List.of(docRequest));

        KycDocument savedDoc = new KycDocument();
        savedDoc.setId(UUID.randomUUID());
        savedDoc.setCustomer(customer);
        savedDoc.setDocumentType(DocumentType.PAN);
        savedDoc.setDocumentNumber("ABCDE1234F");
        savedDoc.setStatus(DocumentStatus.PENDING);

        when(customerRepository.findByCustomerId("CIF001234")).thenReturn(Optional.of(customer));
        when(kycDocumentRepository.saveAll(anyList())).thenReturn(List.of(savedDoc));
        when(kycDocumentRepository.findByCustomerId(customer.getId())).thenReturn(List.of(savedDoc));
        when(customerRepository.save(any(Customer.class))).thenReturn(customer);

        // When
        KycStatusResponse response = customerService.submitKyc("CIF001234", request);

        // Then
        assertThat(response.getCustomerId()).isEqualTo("CIF001234");
        assertThat(response.getKycStatus()).isEqualTo(KycStatus.IN_PROGRESS);
        assertThat(response.getDocuments()).hasSize(1);
        verify(kycDocumentRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("Should verify KYC documents and mark customer status as VERIFIED")
    void testVerifyKycSuccess() {
        // Given
        KycDocument pendingDoc = new KycDocument();
        pendingDoc.setId(UUID.randomUUID());
        pendingDoc.setCustomer(customer);
        pendingDoc.setDocumentType(DocumentType.AADHAAR);
        pendingDoc.setDocumentNumber("123456789012");
        pendingDoc.setStatus(DocumentStatus.PENDING);

        KycVerifyRequest request = new KycVerifyRequest();
        request.setStatus(KycStatus.VERIFIED);

        when(customerRepository.findByCustomerId("CIF001234")).thenReturn(Optional.of(customer));
        when(kycDocumentRepository.findByCustomerId(customer.getId())).thenReturn(List.of(pendingDoc));
        when(kycDocumentRepository.saveAll(anyList())).thenReturn(List.of(pendingDoc));
        when(customerRepository.save(any(Customer.class))).thenReturn(customer);

        // When
        KycStatusResponse response = customerService.verifyKyc("CIF001234", request);

        // Then
        assertThat(response.getKycStatus()).isEqualTo(KycStatus.VERIFIED);
        assertThat(pendingDoc.getStatus()).isEqualTo(DocumentStatus.VERIFIED);
        assertThat(pendingDoc.getVerifiedAt()).isNotNull();
        verify(customerRepository).save(customer);
    }

    @Test
    @DisplayName("Should throw exception when verifying KYC for a non-existent customer")
    void testVerifyKyc_CustomerNotFound_throwsException() {
        // Given
        KycVerifyRequest request = new KycVerifyRequest();
        request.setStatus(KycStatus.VERIFIED);

        when(customerRepository.findByCustomerId("CIF000000")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> customerService.verifyKyc("CIF000000", request))
                .isInstanceOf(CustomerNotFoundException.class);

        verify(kycDocumentRepository, never()).findByCustomerId(any());
    }
}
