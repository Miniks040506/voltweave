package io.voltweave.gateway.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

class CorrelationIdWebFilterTests {
    private final CorrelationIdWebFilter filter = new CorrelationIdWebFilter();

    @Test
    void preservesCanonicalCorrelationIdForDownstreamAndResponse() {
        String expected = UUID.randomUUID().toString();
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/organizations")
                        .header(CorrelationIdWebFilter.HEADER, expected)
        );
        var forwarded = new AtomicReference<ServerWebExchange>();

        filter.filter(exchange, value -> {
            forwarded.set(value);
            return Mono.empty();
        }).block();

        assertThat(forwarded.get().getRequest().getHeaders()
                .getFirst(CorrelationIdWebFilter.HEADER)).isEqualTo(expected);
        assertThat(exchange.getResponse().getHeaders()
                .getFirst(CorrelationIdWebFilter.HEADER)).isEqualTo(expected);
    }

    @Test
    void replacesMalformedExternalCorrelationId() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/organizations")
                        .header(CorrelationIdWebFilter.HEADER, "untrusted value")
        );
        var forwarded = new AtomicReference<ServerWebExchange>();

        filter.filter(exchange, value -> {
            forwarded.set(value);
            return Mono.empty();
        }).block();

        String actual = forwarded.get().getRequest().getHeaders()
                .getFirst(CorrelationIdWebFilter.HEADER);
        assertThatCodeIsCanonicalUuid(actual);
        assertThat(exchange.getResponse().getHeaders()
                .getFirst(CorrelationIdWebFilter.HEADER)).isEqualTo(actual);
    }

    private static void assertThatCodeIsCanonicalUuid(String value) {
        assertThat(UUID.fromString(value).toString()).isEqualTo(value);
    }
}
