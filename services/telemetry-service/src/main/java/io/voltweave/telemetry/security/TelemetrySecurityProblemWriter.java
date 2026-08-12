package io.voltweave.telemetry.security;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import io.voltweave.telemetry.http.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class TelemetrySecurityProblemWriter
        implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED, "Unauthorized",
                "A valid Bearer token is required.");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            org.springframework.security.access.AccessDeniedException exception
    ) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "Forbidden",
                "The authenticated identity cannot access this resource.");
    }

    private void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String title,
            String detail
    ) throws IOException {
        Object attribute = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        String correlationId = attribute instanceof String value
                ? value : UUID.randomUUID().toString();
        String json = """
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s","correlationId":"%s"}
                """.formatted(title, status.value(), detail, correlationId).trim();
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json);
    }
}
