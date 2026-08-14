package io.voltweave.settlement.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTests {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void preservesCanonicalGatewayValue() throws Exception {
        String expected = UUID.randomUUID().toString();
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, expected);
        var response = new MockHttpServletResponse();
        var observed = new AtomicReference<String>();

        filter.doFilter(request, response, (filteredRequest, filteredResponse) ->
                observed.set((String) filteredRequest.getAttribute(CorrelationIdFilter.ATTRIBUTE))
        );

        assertThat(observed).hasValue(expected);
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(expected);
    }

    @Test
    void replacesMalformedExternalValue() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "not-a-uuid");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (filteredRequest, filteredResponse) -> { });

        String actual = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(UUID.fromString(actual).toString()).isEqualTo(actual);
    }
}
