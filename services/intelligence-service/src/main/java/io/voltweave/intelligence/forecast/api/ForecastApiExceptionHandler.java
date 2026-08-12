package io.voltweave.intelligence.forecast.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.voltweave.intelligence.http.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ForecastApiExceptionHandler {
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> forbidden(HttpServletRequest request) {
        return problem(request, HttpStatus.FORBIDDEN, "Forbidden",
                "The authenticated identity cannot access this VPP.");
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<ProblemDetail> badRequest(HttpServletRequest request) {
        return problem(request, HttpStatus.BAD_REQUEST,
                "Invalid forecast request", "Forecast request is invalid.");
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ProblemDetail> unavailable(HttpServletRequest request) {
        return problem(request, HttpStatus.UNPROCESSABLE_ENTITY,
                "Forecast unavailable", "Required training data is unavailable.");
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpServletRequest request,
            HttpStatus status,
            String title,
            String detail
    ) {
        var body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        body.setProperty(
                "correlationId", request.getAttribute(CorrelationIdFilter.ATTRIBUTE)
        );
        return ResponseEntity.status(status).body(body);
    }
}
