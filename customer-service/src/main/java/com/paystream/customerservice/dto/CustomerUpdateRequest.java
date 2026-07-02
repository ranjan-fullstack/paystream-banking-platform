package com.paystream.customerservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerUpdateRequest {
    private String firstName;
    private String lastName;

    @Email
    private String email;

    @Pattern(regexp = "[6-9]\\d{9}", message = "mobile must be a valid 10-digit Indian mobile number")
    private String mobile;
}
