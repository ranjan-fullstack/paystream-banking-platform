package com.flipkartclone.inventoryservice.service.impl;

import com.flipkartclone.common.event.ProductCreatedEvent;
import com.flipkartclone.inventoryservice.model.Inventory;
import com.flipkartclone.inventoryservice.repository.InventoryRepository;
import com.flipkartclone.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    // ✅ EXISTING METHOD (GOOD)
    @Override
    public boolean isProductInStock(Long productId) {
        return inventoryRepository.findById(productId)
                .map(inv -> inv.getQuantity() > 0)
                .orElse(false);
    }

    // 🔥 NEW METHOD (VERY IMPORTANT)
    @Override
    public void createStock(ProductCreatedEvent event) {

        // ⚠️ Idempotency check (IMPORTANT)
        if (inventoryRepository.existsById(event.getProductId())) {
            System.out.println("⚠️ Stock already exists for product: " + event.getProductId());
            return;
        }

        Inventory inventory = Inventory.builder()
                .productId(event.getProductId())
                .quantity(100) // default stock
                .build();

        inventoryRepository.save(inventory);

        System.out.println("📦 Stock created for product: " + event.getProductId());
    }
}