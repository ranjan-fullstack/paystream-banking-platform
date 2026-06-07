package com.flipkartclone.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Slf4j
public class ApiKeyFilter implements GlobalFilter, Ordered {

    @Value("${api.key.value:flipkart-internal-key}")
    private String validApiKey;

    // Only paths under /internal/** require an API key (partner/B2B routes)
    private static final List<String> PROTECTED_PREFIXES = List.of("/internal/");

    @Override
    public int getOrder() {
        return -1; // After logging + blocklist, before JWT
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String path = exchange.getRequest().getURI().getPath();

        boolean requiresKey = PROTECTED_PREFIXES.stream().anyMatch(path::startsWith);
        if (!requiresKey) {
            return chain.filter(exchange);
        }

        String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-KEY");
        if (apiKey == null || !apiKey.equals(validApiKey)) {
            log.warn("🔑 Missing or invalid API key for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }
}
