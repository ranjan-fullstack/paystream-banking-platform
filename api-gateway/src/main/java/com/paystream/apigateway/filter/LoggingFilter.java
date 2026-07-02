package com.paystream.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * First filter in the chain (order -3).
 *
 * Responsibilities:
 *   1. Generate X-Correlation-ID if the client didn't send one.
 *   2. Forward it to every downstream service via the mutated request.
 *   3. Echo it back to the caller on the response.
 *   4. Log method, path, status, duration, and correlationId on every request.
 */
@Component
@Slf4j
public class LoggingFilter implements GlobalFilter, Ordered {

    static final String CORRELATION_HEADER = "X-Correlation-ID";

    @Override
    public int getOrder() {
        return -3; // Runs first — generates correlationId before all other filters
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.currentTimeMillis();

        String method = exchange.getRequest().getMethod().name();
        String path   = exchange.getRequest().getURI().getPath();

        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        log.info("→ {} {} cid={}", method, path, correlationId);

        // Forward correlationId downstream and echo it on the response
        final String cid = correlationId;
        ServerWebExchange mutated = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .header(CORRELATION_HEADER, cid)
                        .build())
                .build();

        mutated.getResponse().getHeaders().set(CORRELATION_HEADER, cid);

        return chain.filter(mutated).then(Mono.fromRunnable(() -> {
            long duration = System.currentTimeMillis() - start;
            int  status   = mutated.getResponse().getStatusCode() != null
                    ? mutated.getResponse().getStatusCode().value() : 0;
            log.info("← {} {} | status={} | {}ms | cid={}", method, path, status, duration, cid);
        }));
    }
}
