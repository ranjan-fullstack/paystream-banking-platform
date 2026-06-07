package com.flipkartclone.apigateway.filter;

import com.flipkartclone.apigateway.util.JwtUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter {

    private final JwtUtil jwtUtil;

    @PostConstruct
    public void init() {
        System.out.println("✅ JWT FILTER LOADED");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();

        System.out.println("🔥 JWT FILTER HIT: " + path);

        // ---------------------------
        // SWAGGER / OPENAPI ENDPOINTS
        // ---------------------------
        if (path.contains("/v3/api-docs") ||
                path.contains("/swagger-ui")) {

            return chain.filter(exchange);
        }

        // ---------------------------
        // PUBLIC ENDPOINTS
        // ---------------------------
        if (path.startsWith("/auth/v1")) {
            return chain.filter(exchange);
        }

        // ---------------------------
        // READ AUTH HEADER
        // ---------------------------
        String authHeader = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        try {

            // ---------------------------
            // VALIDATE TOKEN
            // ---------------------------
            jwtUtil.validateToken(token);

            String role = jwtUtil.extractRole(token); // ROLE_USER / ROLE_ADMIN

            // ---------------------------
            // ROLE-BASED ACCESS
            // ---------------------------
            if (path.startsWith("/api/v1/products")) {

                // USER + ADMIN → GET allowed
                if (method == HttpMethod.GET) {
                    return chain.filter(exchange);
                }

                // ADMIN only → POST / PUT / DELETE
                if (!"ROLE_ADMIN".equals(role)) {
                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    return exchange.getResponse().setComplete();
                }
            }

            // ---------------------------
            // FORWARD ROLE DOWNSTREAM
            // ---------------------------
            ServerWebExchange mutated = exchange.mutate()
                    .request(exchange.getRequest().mutate()
                            .header("X-USER-ROLE", role)
                            .build())
                    .build();

            return chain.filter(mutated);

        } catch (Exception ex) {

            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }
}