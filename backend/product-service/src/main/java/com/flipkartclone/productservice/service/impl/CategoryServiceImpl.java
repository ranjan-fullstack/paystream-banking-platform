package com.flipkartclone.productservice.service.impl;

import com.flipkartclone.productservice.dto.CategoryRequest;
import com.flipkartclone.productservice.model.Category;
import com.flipkartclone.productservice.repository.CategoryRepository;
import com.flipkartclone.productservice.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    public Category createCategory(CategoryRequest request) {
        Category category = Category.builder()
                .name(request.getName())
                .build();

        return categoryRepository.save(category);
    }

    @Override
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }
}
