package com.flipkartclone.productservice.mapper;

import com.flipkartclone.productservice.dto.VariantRequest;
import com.flipkartclone.productservice.dto.VariantResponse;
import com.flipkartclone.productservice.model.ProductVariant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface VariantMapper {

    // Request → Entity
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    ProductVariant toEntity(VariantRequest request);

    // Entity → Response
    @Mapping(source = "product.id", target = "productId")
    VariantResponse toResponse(ProductVariant variant);
}
