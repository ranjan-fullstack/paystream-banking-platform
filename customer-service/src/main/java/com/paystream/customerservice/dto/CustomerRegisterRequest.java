package com.paystream.customerservice.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CustomerRegisterRequest {

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Pattern(regexp = "[6-9]\\d{9}", message = "mobile must be a valid 10-digit Indian mobile number")
    private String mobile;

    @NotBlank
    @Pattern(regexp = "[A-Z]{5}\\d{4}[A-Z]", message = "panNumber must match PAN format e.g. ABCDE1234F")
    private String panNumber;

    @NotBlank
    @Pattern(regexp = "\\d{12}", message = "aadhaarNumber must be 12 digits")
    private String aadhaarNumber;

    @NotNull
    @Past
    private LocalDate dateOfBirth;

    @NotNull
    private Long userId;
}
