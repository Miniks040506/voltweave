package io.voltweave.intelligence.http;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-Id";
    public static final String ATTRIBUTE = CorrelationIdFilter.class.getName() + ".id";
    private static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = canonicalUuid(request.getHeader(HEADER));
        request.setAttribute(ATTRIBUTE, correlationId);
        response.setHeader(HEADER, correlationId);
        MDC.put(MDC_KEY, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String canonicalUuid(String value) {
        if (value != null) {
            try {
                var parsed = UUID.fromString(value);
                if (parsed.toString().equals(value)) {
                    return value;
                }
            } catch (IllegalArgumentException ignored) {
                // A new safe ID replaces malformed external input.
            }
        }
        return UUID.randomUUID().toString();
    }
}
