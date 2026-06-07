package com.flipkartclone.productservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VariantResponse {
    private Long id;
    private String variantName;
    private String variantValue;
    private Double additionalPrice;
    private Long productId;
}
