package com.flipkartclone.productservice.mapper;

import com.flipkartclone.productservice.dto.CategoryRequest;
import com.flipkartclone.productservice.dto.CategoryResponse;
import com.flipkartclone.productservice.model.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryMapper INSTANCE = Mappers.getMapper(CategoryMapper.class);

    @Mapping(target =  "id", ignore = true)
    Category toEntity(CategoryRequest request);

    CategoryResponse toResponse(Category category);
}
