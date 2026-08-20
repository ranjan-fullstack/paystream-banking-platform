package com.paystream.apigateway.filter;

import com.paystream.apigateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter {

    private final JwtUtil jwtUtil;
    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();

        log.debug("JWT filter processing: {} {}", method, path);

        if (path.contains("/v3/api-docs") || path.contains("/swagger-ui") || path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        // Only these auth-service paths are genuinely public (no JWT to verify yet).
        // Everything else under /auth/v1/** — including /auth/v1/admin/**,
        // /auth/v1/branch/** and /auth/v1/change-password — needs the caller's
        // role/userId resolved and forwarded downstream like any other protected route.
        boolean isPublicAuthPath = path.equals("/auth/v1/register")
                || path.equals("/auth/v1/login")
                || path.equals("/auth/v1/refresh")
                || path.equals("/auth/v1/logout");

        if (isPublicAuthPath || path.startsWith("/.well-known")) {
            return chain.filter(exchange);
        }

        boolean isAdminProvisioning = path.startsWith("/auth/v1/admin");

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        Claims claims;
        try {
            claims = jwtUtil.extractClaims(token);
        } catch (Exception ex) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String role = claims.get("role", String.class);
        Object userIdObj = claims.get("userId");
        String userId = userIdObj != null ? String.valueOf(userIdObj) : null;
        boolean isFirstLogin = Boolean.TRUE.equals(claims.get("isFirstLogin", Boolean.class));
        String branchCode = claims.get("branchCode", String.class);
        String employeeId = claims.get("employeeId", String.class);
        String jti = claims.getId();

        var requestBuilder = exchange.getRequest().mutate()
                .header("X-USER-ROLE", role)
                .header("X-IS-FIRST-LOGIN", String.valueOf(isFirstLogin));
        if (userId != null) {
            requestBuilder.header("X-USER-ID", userId);
        }
        if (branchCode != null) {
            requestBuilder.header("X-BRANCH-CODE", branchCode);
        }
        if (employeeId != null) {
            requestBuilder.header("X-EMPLOYEE-ID", employeeId);
        }
        ServerWebExchange mutated = exchange.mutate().request(requestBuilder.build()).build();

        boolean isFirstLoginExemptPath = path.equals("/auth/v1/change-password")
                || path.equals("/auth/v1/logout")
                || path.equals("/.well-known/jwks.json");

        if (isFirstLogin && !isFirstLoginExemptPath) {
            return writeFirstLoginRequiredResponse(exchange);
        }

        Mono<Boolean> blacklistCheck = jti != null
                ? redisTemplate.hasKey("blacklist:" + jti).defaultIfEmpty(false).onErrorReturn(false)
                : Mono.just(false);

        return blacklistCheck.flatMap(blacklisted -> {
            if (blacklisted) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            if (path.startsWith("/api/v1/accounts")) {
                if (method == HttpMethod.GET) {
                    return chain.filter(mutated);
                }
                if (!"ADMIN".equals(role) && !"TELLER".equals(role)) {
                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    return exchange.getResponse().setComplete();
                }
            }

            if (isAdminProvisioning && !"ADMIN".equals(role)) {
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }

            return chain.filter(mutated);
        });
    }

    private Mono<Void> writeFirstLoginRequiredResponse(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"error\":\"FIRST_LOGIN_REQUIRED\","
                + "\"message\":\"You must change your temporary password before proceeding.\","
                + "\"redirectTo\":\"/auth/v1/change-password\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
