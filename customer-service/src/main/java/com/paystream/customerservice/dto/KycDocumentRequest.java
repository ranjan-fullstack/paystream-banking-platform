package com.paystream.customerservice.dto;

import com.paystream.customerservice.enums.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KycDocumentRequest {

    @NotNull
    private DocumentType documentType;

    @NotBlank
    private String documentNumber;
}
