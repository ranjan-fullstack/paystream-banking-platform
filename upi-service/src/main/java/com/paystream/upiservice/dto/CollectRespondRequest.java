package com.paystream.upiservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CollectRespondRequest {

    @NotNull
    private boolean accept;

    private String upiPin; // required when accept = true
}
