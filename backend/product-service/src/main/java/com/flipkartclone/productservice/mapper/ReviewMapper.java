package com.flipkartclone.productservice.mapper;

import com.flipkartclone.productservice.dto.ReviewResponse;
import com.flipkartclone.productservice.model.Review;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface ReviewMapper {

    ReviewMapper INSTANCE = Mappers.getMapper(ReviewMapper.class);

    ReviewResponse toResponse(Review review);
}
