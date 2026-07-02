package com.paystream.upiservice.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * There is no standalone VpaValidator class in upi-service; the handle format is enforced
 * declaratively via @Pattern on VpaRegisterRequest.handle. These tests exercise that real
 * constraint directly through Bean Validation.
 */
@DisplayName("VPA handle format validation unit tests")
class VpaValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private VpaRegisterRequest buildRequest(String handle) {
        VpaRegisterRequest request = new VpaRegisterRequest();
        request.setCustomerId("CIF004455");
        request.setAccountNumber("1111222233334444");
        request.setHandle(handle);
        return request;
    }

    @Test
    @DisplayName("Should accept a VPA handle containing only letters, digits, dot, underscore and hyphen")
    void testValidVpaFormat_returnsTrue() {
        // Given
        VpaRegisterRequest request = buildRequest("priya.sharma_92");

        // When
        Set<ConstraintViolation<VpaRegisterRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Should reject a VPA handle shorter than the minimum allowed length")
    void testInvalidVpaFormat_returnsFalse() {
        // Given
        VpaRegisterRequest request = buildRequest("ab");

        // When
        Set<ConstraintViolation<VpaRegisterRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("Should reject a VPA handle containing special characters such as '@' or '!'")
    void testVpaWithSpecialChars_returnsFalse() {
        // Given
        VpaRegisterRequest request = buildRequest("priya@sharma!");

        // When
        Set<ConstraintViolation<VpaRegisterRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
    }
}
