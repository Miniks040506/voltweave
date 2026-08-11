package io.voltweave.portfolio.http;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-Id";
    public static final String ATTRIBUTE = CorrelationIdFilter.class.getName() + ".value";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = resolve(request.getHeader(HEADER));
        request.setAttribute(ATTRIBUTE, correlationId);
        response.setHeader(HEADER, correlationId);
        MDC.put("correlationId", correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");
        }
    }

    private static String resolve(String candidate) {
        if (candidate != null) {
            try {
                String normalized = UUID.fromString(candidate).toString();
                if (normalized.equalsIgnoreCase(candidate)) {
                    return normalized;
                }
            } catch (IllegalArgumentException ignored) {
                // A malformed external value is replaced, never reflected.
            }
        }
        return UUID.randomUUID().toString();
    }
}
