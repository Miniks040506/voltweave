package io.voltweave.telemetry.query.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.voltweave.telemetry.http.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class TelemetryApiExceptionHandler {
    @ExceptionHandler({
            IllegalArgumentException.class,
            MissingServletRequestParameterException.class
    })
    ResponseEntity<ProblemDetail> badRequest(HttpServletRequest request) {
        var body = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Invalid telemetry query"
        );
        body.setTitle("Invalid telemetry query");
        body.setProperty(
                "correlationId", request.getAttribute(CorrelationIdFilter.ATTRIBUTE)
        );
        return ResponseEntity.badRequest().body(body);
    }
}
