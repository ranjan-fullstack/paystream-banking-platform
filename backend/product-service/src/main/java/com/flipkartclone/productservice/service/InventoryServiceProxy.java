package com.flipkartclone.productservice.service;

import com.flipkartclone.productservice.client.InventoryClient;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@RefreshScope
@Service
@RequiredArgsConstructor
public class InventoryServiceProxy {

    private final InventoryClient inventoryClient;

    @TimeLimiter(name = "inventoryService", fallbackMethod = "fallbackCheckStock")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "fallbackCheckStock")
    @Retry(name = "inventoryService")
    @RateLimiter(name = "inventoryService")
    @Bulkhead(name = "inventoryService", type = Bulkhead.Type.SEMAPHORE)
    public CompletableFuture<Boolean> checkStock(Long productId) {
        return CompletableFuture.supplyAsync(() -> inventoryClient.isInStock(productId));
    }

    public CompletableFuture<Boolean> fallbackCheckStock(Long productId, Throwable ex) {
        System.out.println("🔥 FALLBACK: Inventory service DOWN -> " + ex.getMessage());
        return CompletableFuture.completedFuture(false);
    }
}
