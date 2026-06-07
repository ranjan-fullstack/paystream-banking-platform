package com.flipkartclone.productservice.dto;

import com.flipkartclone.productservice.validation.ValidPriceDiscount;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
@ValidPriceDiscount
public class ProductRequest {

    @NotBlank(message = "Title is required")
    @Size(min = 3, max = 255, message = "Title must be between 3 and 255 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 2000, message = "Description must be between 10 and 2000 characters")
    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    private Double price;

    @DecimalMin(value = "0.0", message = "Discount cannot be negative")
    @DecimalMax(value = "99999.0", message = "Discount is too high")
    private Double discount;

    @NotBlank(message = "Brand is required")
    @Pattern(regexp = "^[a-zA-Z0-9 &.-]{2,100}$", message = "Brand name contains invalid characters")
    private String brand;

    @NotNull(message = "Stock is required")
    @Min(value = 0, message = "Stock cannot be negative")
    @Max(value = 100000, message = "Stock cannot exceed 100,000")
    private Integer stock;

    @Pattern(regexp = "^(https?://.*\\.(jpg|jpeg|png|webp|gif))$",
             message = "Image URL must be a valid URL ending in jpg, jpeg, png, webp, or gif")
    private String imageUrl;

    @DecimalMin(value = "0.0", message = "Rating cannot be negative")
    @DecimalMax(value = "5.0", message = "Rating cannot exceed 5")
    private Double rating;

    @NotNull(message = "Category is required")
    private Long categoryId;
}
