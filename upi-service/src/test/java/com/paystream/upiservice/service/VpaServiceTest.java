package com.paystream.upiservice.service;

import com.paystream.upiservice.dto.SetPinRequest;
import com.paystream.upiservice.dto.VpaRegisterRequest;
import com.paystream.upiservice.dto.VpaResponse;
import com.paystream.upiservice.entity.VirtualPaymentAddress;
import com.paystream.upiservice.exception.VpaNotFoundException;
import com.paystream.upiservice.repository.VpaRepository;
import com.paystream.upiservice.service.impl.VpaServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VpaService unit tests")
class VpaServiceTest {

    @Mock
    private VpaRepository vpaRepository;

    @InjectMocks
    private VpaServiceImpl vpaService;

    @Test
    @DisplayName("Should register a new VPA successfully as the customer's default")
    void testRegisterVpa_Success() {
        // Given
        VpaRegisterRequest request = new VpaRegisterRequest();
        request.setCustomerId("CIF004455");
        request.setAccountNumber("1111222233334444");
        request.setHandle("priya.sharma");

        when(vpaRepository.existsByVpa("priya.sharma@paystream")).thenReturn(false);
        when(vpaRepository.findByCustomerId("CIF004455")).thenReturn(List.of());
        when(vpaRepository.save(any(VirtualPaymentAddress.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        VpaResponse response = vpaService.register(request);

        // Then
        assertThat(response.getVpa()).isEqualTo("priya.sharma@paystream");
        assertThat(response.isDefault()).isTrue();
        assertThat(response.isActive()).isTrue();
    }

    @Test
    @DisplayName("Should throw exception when registering a VPA handle that is already taken")
    void testRegisterVpa_Duplicate_throwsException() {
        // Given
        VpaRegisterRequest request = new VpaRegisterRequest();
        request.setCustomerId("CIF004455");
        request.setAccountNumber("1111222233334444");
        request.setHandle("priya.sharma");

        when(vpaRepository.existsByVpa("priya.sharma@paystream")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> vpaService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("priya.sharma@paystream");

        verify(vpaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should return the VPA details when looking up an existing VPA")
    void testValidateVpa_exists_returnsTrue() {
        // Given
        VirtualPaymentAddress entity = new VirtualPaymentAddress();
        entity.setVpa("priya.sharma@paystream");
        entity.setCustomerId("CIF004455");
        entity.setAccountNumber("1111222233334444");
        entity.setActive(true);

        when(vpaRepository.findByVpa("priya.sharma@paystream")).thenReturn(Optional.of(entity));

        // When
        VpaResponse response = vpaService.getByVpa("priya.sharma@paystream");

        // Then
        assertThat(response.getVpa()).isEqualTo("priya.sharma@paystream");
        assertThat(response.isActive()).isTrue();
    }

    @Test
    @DisplayName("Should throw exception when looking up a VPA that does not exist")
    void testValidateVpa_notExists_returnsFalse() {
        // Given - VpaService has no boolean validate(); a non-existent VPA surfaces as VpaNotFoundException instead.
        when(vpaRepository.findByVpa("ghost.user@paystream")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> vpaService.getByVpa("ghost.user@paystream"))
                .isInstanceOf(VpaNotFoundException.class);
    }

    @Test
    @DisplayName("Should set and hash the UPI PIN for an existing VPA")
    void testSetUpiPin_Success() {
        // Given
        SetPinRequest request = new SetPinRequest();
        request.setVpa("priya.sharma@paystream");
        request.setUpiPin("4455");

        VirtualPaymentAddress entity = new VirtualPaymentAddress();
        entity.setVpa("priya.sharma@paystream");
        entity.setCustomerId("CIF004455");
        entity.setAccountNumber("1111222233334444");

        when(vpaRepository.findByVpa("priya.sharma@paystream")).thenReturn(Optional.of(entity));
        when(vpaRepository.save(any(VirtualPaymentAddress.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        vpaService.setPin(request);

        // Then
        ArgumentCaptor<VirtualPaymentAddress> captor = ArgumentCaptor.forClass(VirtualPaymentAddress.class);
        verify(vpaRepository).save(captor.capture());
        assertThat(captor.getValue().getUpiPin()).isNotEqualTo("4455");
        assertThat(captor.getValue().getUpiPin()).isNotBlank();
    }
}
