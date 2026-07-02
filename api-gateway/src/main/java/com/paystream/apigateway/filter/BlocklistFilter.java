package com.paystream.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
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
public class BlocklistFilter implements GlobalFilter, Ordered {

    private static final long MAX_BODY_BYTES = 10 * 1024 * 1024; // 10 MB

    // Path traversal + common injection patterns
    private static final List<String> BLOCKED_PATTERNS = List.of(
            "../", "..\\",
            "<script>", "</script>",
            "SELECT ", "DROP ", "INSERT ", "DELETE ", "UPDATE ", "UNION ",
            "--", "1=1", "OR 1"
    );

    @Override
    public int getOrder() {
        return -2; // After logging, before API key and JWT
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String path  = exchange.getRequest().getURI().getRawPath();
        String query = exchange.getRequest().getURI().getRawQuery();
        String full  = (path + (query != null ? "?" + query : "")).toUpperCase();

        // Block injection / traversal patterns
        for (String pattern : BLOCKED_PATTERNS) {
            if (full.contains(pattern.toUpperCase())) {
                log.warn("🚨 Blocked malicious request [{} {}] — matched pattern: {}",
                        exchange.getRequest().getMethod(), path, pattern);
                exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                return exchange.getResponse().setComplete();
            }
        }

        // Block oversized payloads
        String contentLength = exchange.getRequest().getHeaders().getFirst("Content-Length");
        if (contentLength != null) {
            try {
                if (Long.parseLong(contentLength) > MAX_BODY_BYTES) {
                    log.warn("🚨 Blocked oversized request: {} bytes from {}",
                            contentLength, exchange.getRequest().getRemoteAddress());
                    exchange.getResponse().setStatusCode(HttpStatus.PAYLOAD_TOO_LARGE);
                    return exchange.getResponse().setComplete();
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return chain.filter(exchange);
    }
}
