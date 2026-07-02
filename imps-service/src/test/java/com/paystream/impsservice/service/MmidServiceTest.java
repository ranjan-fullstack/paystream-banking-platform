package com.paystream.impsservice.service;

import com.paystream.impsservice.dto.MmidRegisterRequest;
import com.paystream.impsservice.dto.MmidResponse;
import com.paystream.impsservice.entity.MmidRegistration;
import com.paystream.impsservice.repository.MmidRegistrationRepository;
import com.paystream.impsservice.service.impl.MmidServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MmidService unit tests")
class MmidServiceTest {

    @Mock
    private MmidRegistrationRepository mmidRegistrationRepository;

    @InjectMocks
    private MmidServiceImpl mmidService;

    @Test
    @DisplayName("Should register a new MMID for a mobile number successfully")
    void testRegisterMmid_Success() {
        // Given
        MmidRegisterRequest request = new MmidRegisterRequest();
        request.setCustomerId("CIF002211");
        request.setAccountNumber("1111222233334444");
        request.setMobileNumber("9876501234");

        when(mmidRegistrationRepository.findByMobileNumber("9876501234")).thenReturn(Optional.empty());
        when(mmidRegistrationRepository.existsByMmid(anyString())).thenReturn(false);
        when(mmidRegistrationRepository.save(any(MmidRegistration.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        MmidResponse response = mmidService.register(request);

        // Then
        assertThat(response.getMmid()).matches("\\d{7}");
        assertThat(response.getAccountNumber()).isEqualTo("1111222233334444");
        assertThat(response.isActive()).isTrue();
    }

    @Test
    @DisplayName("Should keep the existing MMID unchanged when re-registering the same mobile number")
    void testRegisterMmid_Duplicate_keepsExistingMmid() {
        // Given - MmidServiceImpl.register() is an upsert: re-registering an already-registered mobile
        // number updates the account binding but does not regenerate or reject the existing MMID.
        MmidRegisterRequest request = new MmidRegisterRequest();
        request.setCustomerId("CIF002211");
        request.setAccountNumber("9999888877776666");
        request.setMobileNumber("9876501234");

        MmidRegistration existing = new MmidRegistration();
        existing.setCustomerId("CIF002211");
        existing.setAccountNumber("1111222233334444");
        existing.setMobileNumber("9876501234");
        existing.setMmid("1234567");
        existing.setActive(true);

        when(mmidRegistrationRepository.findByMobileNumber("9876501234")).thenReturn(Optional.of(existing));
        when(mmidRegistrationRepository.save(any(MmidRegistration.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        MmidResponse response = mmidService.register(request);

        // Then
        assertThat(response.getMmid()).isEqualTo("1234567");
        assertThat(response.getAccountNumber()).isEqualTo("9999888877776666");
        verify(mmidRegistrationRepository, never()).existsByMmid(anyString());
    }
}
