package com.paystream.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Adds the two rate-limit headers that Spring Cloud Gateway's RedisRateLimiter
 * does NOT emit by default:
 *
 *   X-RateLimit-Reset  — Unix epoch second when the token bucket replenishes.
 *                        Present on every response so clients can pre-schedule
 *                        retries instead of guessing the window size.
 *                        Value = current_second + 1  (1-second token-bucket window).
 *
 *   Retry-After        — Present only on 429 Too Many Requests.
 *                        Tells the client how many seconds to wait (1 second for
 *                        the standard replenishment period).
 *
 * Spring Cloud Gateway's RedisRateLimiter already adds:
 *   X-RateLimit-Remaining, X-RateLimit-Burst-Capacity,
 *   X-RateLimit-Replenish-Rate, X-RateLimit-Requested-Tokens
 * This filter only fills the gaps.
 *
 * Headers are injected via beforeCommit() — this callback fires after the
 * rate-limiter route filter has evaluated the request and set the response
 * status, but before the response is flushed to the client. Order of this
 * GlobalFilter in the pre-chain does not affect when beforeCommit fires.
 */
@Component
@Slf4j
public class RateLimitHeaderFilter implements GlobalFilter, Ordered {

    static final String HEADER_RESET = "X-RateLimit-Reset";
    static final String HEADER_RETRY = "Retry-After";

    @Override
    public int getOrder() {
        return 2; // After IdempotencyFilter (1)
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            long resetEpochSecond = Instant.now().getEpochSecond() + 1;
            exchange.getResponse().getHeaders()
                    .set(HEADER_RESET, String.valueOf(resetEpochSecond));

            if (HttpStatus.TOO_MANY_REQUESTS.equals(exchange.getResponse().getStatusCode())) {
                exchange.getResponse().getHeaders().set(HEADER_RETRY, "1");
                log.warn("Rate limit exceeded: {} {}",
                        exchange.getRequest().getMethod(),
                        exchange.getRequest().getURI().getPath());
            }

            return Mono.empty();
        });

        return chain.filter(exchange);
    }
}
