package com.flipkartclone.inventoryservice.service;

import com.flipkartclone.common.event.ProductCreatedEvent;

public interface InventoryService {
    boolean isProductInStock(Long productId);
    void createStock(ProductCreatedEvent event); // 🔥 ADD THIS
}
