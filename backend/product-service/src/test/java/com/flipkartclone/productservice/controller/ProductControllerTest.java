package com.flipkartclone.productservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkartclone.productservice.dto.ProductRequest;
import com.flipkartclone.productservice.dto.ProductResponse;
import com.flipkartclone.productservice.service.ImageStorageService;
import com.flipkartclone.productservice.service.ProductService;
import com.flipkartclone.productservice.repository.ProductRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService service;

    @MockBean
    private ProductRepository productRepository;

    @MockBean
    private ImageStorageService imageStorageService;

    // -------------------------------------------------------
    // GET by ID — happy path
    // -------------------------------------------------------
    @Test
    void shouldReturnProduct() throws Exception {

        ProductResponse response = new ProductResponse();
        response.setId(1L);
        response.setTitle("Laptop");
        response.setDescription("Gaming Laptop");
        response.setPrice(80000.0);
        response.setBrand("Dell");

        when(service.getProductById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/products/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Laptop"))
                .andExpect(jsonPath("$.brand").value("Dell"));
    }

    // -------------------------------------------------------
    // POST — valid request returns 200
    // -------------------------------------------------------
    @Test
    void shouldCreateProduct() throws Exception {

        ProductRequest request = new ProductRequest();
        request.setTitle("Samsung TV");
        request.setDescription("55 inch 4K Smart TV");
        request.setPrice(60000.0);
        request.setBrand("Samsung");
        request.setStock(5);
        request.setCategoryId(1L);

        ProductResponse response = new ProductResponse();
        response.setId(2L);
        response.setTitle("Samsung TV");

        when(service.addProduct(request)).thenReturn(response);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Samsung TV"));
    }

    // -------------------------------------------------------
    // POST — blank title triggers @Valid → 400
    // -------------------------------------------------------
    @Test
    void shouldRejectProductWithBlankTitle() throws Exception {

        ProductRequest request = new ProductRequest();
        request.setTitle("");                       // @NotBlank violation
        request.setDescription("Some description that is long enough");
        request.setPrice(60000.0);
        request.setBrand("Samsung");
        request.setStock(5);
        request.setCategoryId(1L);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------
    // POST — discount >= price triggers custom @ValidPriceDiscount → 400
    // -------------------------------------------------------
    @Test
    void shouldRejectProductWhenDiscountExceedsPrice() throws Exception {

        ProductRequest request = new ProductRequest();
        request.setTitle("Test Product");
        request.setDescription("Valid description here for testing");
        request.setPrice(1000.0);
        request.setDiscount(2000.0);               // discount > price — custom validator fails
        request.setBrand("TestBrand");
        request.setStock(10);
        request.setCategoryId(1L);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
