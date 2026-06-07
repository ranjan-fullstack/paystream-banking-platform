package com.flipkartclone.productservice.service;

import com.flipkartclone.productservice.dto.CategoryRequest;
import com.flipkartclone.productservice.model.Category;

import java.util.List;

public interface CategoryService {
    Category createCategory(CategoryRequest request);
    List<Category> getAllCategories();
}
