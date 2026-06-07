package com.flipkartclone.inventoryservice.controller;

import com.flipkartclone.inventoryservice.service.InventoryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/check")
    public boolean checkStock(@RequestParam("productId") Long productId) {
        return inventoryService.isProductInStock(productId);
    }
}
