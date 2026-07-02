package com.paystream.upiservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetPinRequest {

    @NotBlank
    private String vpa;

    @NotBlank
    @Pattern(regexp = "\\d{4}|\\d{6}", message = "upiPin must be 4 or 6 digits")
    private String upiPin;
}
