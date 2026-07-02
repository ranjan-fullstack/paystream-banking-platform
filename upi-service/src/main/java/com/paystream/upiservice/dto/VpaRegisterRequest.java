package com.paystream.upiservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VpaRegisterRequest {

    @NotBlank
    private String customerId;

    @NotBlank
    private String accountNumber;

    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9._-]{3,50}", message = "handle may only contain letters, digits, dot, underscore, hyphen")
    private String handle; // part before @paystream
}
