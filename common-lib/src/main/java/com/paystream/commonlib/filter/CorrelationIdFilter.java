package com.paystream.commonlib.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Reads X-Correlation-ID from the incoming request (or generates a UUID if absent),
 * places it in the SLF4J MDC under "correlationId", and echoes it back on the response.
 *
 * Any service that depends on common-lib gets this filter auto-registered at
 * Ordered.HIGHEST_PRECEDENCE via CorrelationAutoConfiguration.
 *
 * Downstream Feign calls pick it up via FeignCorrelationConfig (per-service bean).
 * Async thread-pool tasks must copy MDC via AsyncConfig.setTaskDecorator().
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER  = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String id = request.getHeader(HEADER);
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
