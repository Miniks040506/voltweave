package io.voltweave.gateway.http;

import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {
    public static final String HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = resolve(exchange.getRequest().getHeaders().getFirst(HEADER));
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(HEADER, correlationId))
                .build();
        exchange.getResponse().getHeaders().set(HEADER, correlationId);
        return chain.filter(exchange.mutate().request(request).build());
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
