package com.flipkartclone.productservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VariantRequest {

    @NotBlank(message = "Variant name is required (e.g. Color, RAM)")
    @Size(max = 50, message = "Variant name must not exceed 50 characters")
    private String variantName;

    @NotBlank(message = "Variant value is required (e.g. Black, 8GB)")
    @Size(max = 100, message = "Variant value must not exceed 100 characters")
    private String variantValue;

    @DecimalMin(value = "0.0", message = "Additional price cannot be negative")
    private Double additionalPrice;
}
