package com.flipkartclone.productservice.integration;

import com.flipkartclone.productservice.model.Category;
import com.flipkartclone.productservice.model.Product;
import com.flipkartclone.productservice.repository.CategoryRepository;
import com.flipkartclone.productservice.repository.ProductRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ProductIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Category savedCategory;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        savedCategory = categoryRepository.save(Category.builder().name("Electronics").build());
    }

    // -------------------------------------------------------
    // Save
    // -------------------------------------------------------
    @Test
    void shouldSaveProduct() {
        Product saved = productRepository.save(Product.builder()
                .title("Samsung TV")
                .description("Smart TV")
                .price(50000.0)
                .brand("Samsung")
                .stock(10)
                .deleted(false)
                .category(savedCategory)
                .build());

        assertNotNull(saved.getId());
        assertEquals("Samsung TV", saved.getTitle());
    }

    // -------------------------------------------------------
    // Update
    // -------------------------------------------------------
    @Test
    void shouldUpdateProduct() {
        Product product = productRepository.save(Product.builder()
                .title("Old Title")
                .description("Old description text here")
                .price(10000.0)
                .brand("OldBrand")
                .stock(5)
                .deleted(false)
                .category(savedCategory)
                .build());

        product.setTitle("New Title");
        product.setPrice(15000.0);
        Product updated = productRepository.save(product);

        assertEquals("New Title", updated.getTitle());
        assertEquals(15000.0, updated.getPrice());
    }

    // -------------------------------------------------------
    // Soft delete — deleted products must not be returned
    // -------------------------------------------------------
    @Test
    void shouldExcludeSoftDeletedProducts() {
        productRepository.save(Product.builder()
                .title("Active Product")
                .description("This product is active")
                .price(5000.0)
                .brand("BrandA")
                .stock(10)
                .deleted(false)
                .category(savedCategory)
                .build());

        productRepository.save(Product.builder()
                .title("Deleted Product")
                .description("This product was removed")
                .price(3000.0)
                .brand("BrandB")
                .stock(0)
                .deleted(true)
                .category(savedCategory)
                .build());

        List<Product> active = productRepository.findByDeletedFalse();

        assertEquals(1, active.size());
        assertEquals("Active Product", active.get(0).getTitle());
    }

    // -------------------------------------------------------
    // Soft delete — findByIdAndDeletedFalse returns empty for deleted
    // -------------------------------------------------------
    @Test
    void shouldNotFindDeletedProductById() {
        Product deleted = productRepository.save(Product.builder()
                .title("Ghost Product")
                .description("Soft deleted product")
                .price(2000.0)
                .brand("BrandC")
                .stock(0)
                .deleted(true)
                .category(savedCategory)
                .build());

        Optional<Product> result = productRepository.findByIdAndDeletedFalse(deleted.getId());

        assertTrue(result.isEmpty());
    }

    // -------------------------------------------------------
    // Optimistic locking — version increments on each save
    // -------------------------------------------------------
    @Test
    void shouldIncrementVersionOnUpdate() {
        Product product = productRepository.save(Product.builder()
                .title("Versioned Product")
                .description("Testing optimistic locking")
                .price(8000.0)
                .brand("BrandD")
                .stock(3)
                .deleted(false)
                .category(savedCategory)
                .build());

        Long versionAfterCreate = product.getVersion();

        product.setPrice(9000.0);
        Product updated = productRepository.save(product);

        assertTrue(updated.getVersion() > versionAfterCreate);
    }
}
