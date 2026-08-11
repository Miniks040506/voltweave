package io.voltweave.gateway.security;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import io.voltweave.gateway.http.CorrelationIdWebFilter;
import reactor.core.publisher.Mono;

@Component
public class GatewaySecurityProblemWriter
        implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    @Override
    public Mono<Void> commence(
            ServerWebExchange exchange,
            AuthenticationException exception
    ) {
        return write(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized",
                "A valid Bearer token is required.");
    }

    @Override
    public Mono<Void> handle(
            ServerWebExchange exchange,
            org.springframework.security.access.AccessDeniedException exception
    ) {
        return write(exchange, HttpStatus.FORBIDDEN, "Forbidden",
                "The authenticated identity cannot access this resource.");
    }

    private Mono<Void> write(
            ServerWebExchange exchange,
            HttpStatus status,
            String title,
            String detail
    ) {
        String correlationId = exchange.getResponse().getHeaders()
                .getFirst(CorrelationIdWebFilter.HEADER);
        String json = """
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s","correlationId":"%s"}
                """.formatted(title, status.value(), detail, correlationId).trim();
        byte[] body = json.getBytes(UTF_8);

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        exchange.getResponse().getHeaders().setContentLength(body.length);
        return exchange.getResponse().writeWith(Mono.just(
                exchange.getResponse().bufferFactory().wrap(body)
        ));
    }
}
