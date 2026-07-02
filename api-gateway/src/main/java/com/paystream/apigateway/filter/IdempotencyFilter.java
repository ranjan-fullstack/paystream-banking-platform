package com.paystream.apigateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

/**
 * Enforces idempotency on payment-initiation endpoints (NEFT/RTGS/IMPS/UPI
 * transfers and pay/collect requests).
 *
 * Clients must send X-Idempotency-Key (UUID) on every mutating request.
 * If the same key is submitted twice within 24 hours, the second request
 * gets HTTP 409 — preventing double-charges on network retries.
 *
 * Flow:
 *   1. Missing key on protected endpoint → 400
 *   2. Key already in Redis             → 409 (duplicate detected)
 *   3. Key not in Redis                 → store key (TTL 24h) → forward downstream
 *
 * The key is also forwarded to downstream services so they can apply
 * their own DB-level deduplication if needed.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class IdempotencyFilter implements GlobalFilter, Ordered {

    static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    private static final String REDIS_PREFIX = "idempotency:";
    private static final Duration TTL = Duration.ofHours(24);

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/v1/neft/transfer",
            "/api/v1/rtgs/transfer",
            "/api/v1/imps/transfer",
            "/api/v1/upi/pay",
            "/api/v1/upi/collect",
            "/api/v1/upi/refund"
    );

    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public int getOrder() {
        // After JWT auth (implicit 0), before forwarding to downstream services
        return 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();

        if (!requiresIdempotency(path, method)) {
            return chain.filter(exchange);
        }

        String key = exchange.getRequest().getHeaders().getFirst(IDEMPOTENCY_HEADER);

        if (key == null || key.isBlank()) {
            log.warn("Missing {} on {} {}", IDEMPOTENCY_HEADER, method, path);
            return writeError(exchange, HttpStatus.BAD_REQUEST,
                    "X-Idempotency-Key header is required for order and payment operations");
        }

        String redisKey = REDIS_PREFIX + key;

        return redisTemplate.opsForValue().get(redisKey)
                .flatMap(existing -> {
                    log.warn("Duplicate idempotency key={} path={}", key, path);
                    return writeError(exchange, HttpStatus.CONFLICT,
                            "Duplicate request: idempotency key already used within 24 hours. "
                                    + "Use a new UUID key to submit a different request.");
                })
                .switchIfEmpty(
                        redisTemplate.opsForValue().set(redisKey, "PROCESSING", TTL)
                                .then(chain.filter(exchange))
                );
    }

    private boolean requiresIdempotency(String path, HttpMethod method) {
        return method == HttpMethod.POST
                && PROTECTED_PATHS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"status":%d,"error":"%s","message":"%s"}"""
                .formatted(status.value(), status.getReasonPhrase(), message);
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
