package com.flipkartclone.productservice.container;

import com.flipkartclone.productservice.model.Category;
import com.flipkartclone.productservice.model.Product;
import com.flipkartclone.productservice.repository.CategoryRepository;
import com.flipkartclone.productservice.repository.ProductRepository;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Requires active Docker daemon")
@Testcontainers
@SpringBootTest
class ProductContainerTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",        postgres::getJdbcUrl);
        registry.add("spring.datasource.username",   postgres::getUsername);
        registry.add("spring.datasource.password",   postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled",         () -> "false");
    }

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void shouldSaveAndFindProduct() {

        Category category = categoryRepository.save(
                Category.builder().name("Electronics").build());

        Product product = productRepository.save(Product.builder()
                .title("iPhone 16")
                .description("Apple Mobile")
                .price(120000.0)
                .brand("Apple")
                .stock(5)
                .deleted(false)
                .category(category)
                .build());

        assertNotNull(product.getId());
        assertEquals("iPhone 16", product.getTitle());
    }

    @Test
    void shouldNotReturnSoftDeletedProduct() {

        Category category = categoryRepository.save(
                Category.builder().name("Mobiles").build());

        Product product = productRepository.save(Product.builder()
                .title("Deleted Phone")
                .description("This product is soft deleted")
                .price(30000.0)
                .brand("Nokia")
                .stock(0)
                .deleted(true)
                .category(category)
                .build());

        assertTrue(productRepository.findByIdAndDeletedFalse(product.getId()).isEmpty());
    }
}
