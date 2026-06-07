package com.flipkartclone.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(min = 2, max = 100, message = "Category name must be between 2 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9 &-]+$", message = "Category name contains invalid characters")
    private String name;
}
