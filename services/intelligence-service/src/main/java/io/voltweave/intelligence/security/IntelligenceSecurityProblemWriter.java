package io.voltweave.intelligence.security;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import io.voltweave.intelligence.http.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@Component
public class IntelligenceSecurityProblemWriter {
    private final ObjectMapper objectMapper;

    public IntelligenceSecurityProblemWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void unauthorized(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED,
                "Unauthorized", "A valid Bearer token is required.");
    }

    public void forbidden(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        write(request, response, HttpStatus.FORBIDDEN,
                "Forbidden", "The authenticated identity cannot access this resource.");
    }

    private void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String title,
            String detail
    ) throws IOException {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("about:blank"));
        problem.setProperty(
                "correlationId", request.getAttribute(CorrelationIdFilter.ATTRIBUTE)
        );
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
