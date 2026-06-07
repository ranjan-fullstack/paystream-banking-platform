package com.flipkartclone.productservice.repository;

import com.flipkartclone.productservice.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;


public interface CategoryRepository extends JpaRepository<Category, Long> {
}
