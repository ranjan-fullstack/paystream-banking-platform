package com.paystream.customerservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class KycSubmitRequest {

    @NotEmpty
    @Valid
    private List<KycDocumentRequest> documents;
}
