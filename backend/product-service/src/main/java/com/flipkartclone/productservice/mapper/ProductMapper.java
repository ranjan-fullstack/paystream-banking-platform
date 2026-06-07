package com.flipkartclone.productservice.mapper;

import com.flipkartclone.productservice.dto.ProductRequest;
import com.flipkartclone.productservice.dto.ProductResponse;
import com.flipkartclone.productservice.dto.ReviewResponse;
import com.flipkartclone.productservice.model.Product;
import com.flipkartclone.productservice.model.Review;
import org.mapstruct.*;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    // REQUEST → ENTITY
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "category", ignore = true)
    Product toEntity(ProductRequest request);

    // ENTITY → RESPONSE (without reviews & avg rating)
    @Mapping(source = "category.name", target = "categoryName")
    @Mapping(target = "reviews", ignore = true)
    @Mapping(target = "averageRating", ignore = true)
    ProductResponse toBaseResponse(Product product);

    // MAPSTRUCT helper to map Review entity → DTO
    @Mapping(source = "userId", target = "userId")
    ReviewResponse toReviewResponse(Review review);

    // CUSTOM METHOD to combine everything (reviews + avg rating)
    default ProductResponse toResponse(Product product) {

        ProductResponse response = toBaseResponse(product);

        // REVIEWS
        if (product.getReviews() != null) {
            List<ReviewResponse> reviewDTOs =
                    product.getReviews().stream()
                            .map(this::toReviewResponse)
                            .toList();
            response.setReviews(reviewDTOs);

            // AVERAGE RATING
            double avg = product.getReviews().stream()
                    .mapToDouble(Review::getRating)
                    .average()
                    .orElse(0.0);

            response.setAverageRating(avg);
        } else {
            response.setAverageRating(0.0);
        }

        return response;
    }
}
