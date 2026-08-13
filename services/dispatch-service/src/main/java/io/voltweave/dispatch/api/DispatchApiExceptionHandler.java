package io.voltweave.dispatch.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.voltweave.dispatch.exception.IdempotencyConflictException;
import io.voltweave.dispatch.http.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class DispatchApiExceptionHandler {
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> forbidden(HttpServletRequest request) {
        return problem(request, HttpStatus.FORBIDDEN, "Forbidden",
                "The authenticated identity cannot access this dispatch.");
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<ProblemDetail> conflict(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "Idempotency conflict",
                "The Idempotency-Key was already used with another request.");
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<ProblemDetail> badRequest(HttpServletRequest request) {
        return problem(request, HttpStatus.BAD_REQUEST, "Invalid request", "Request is invalid.");
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ProblemDetail> unavailable(HttpServletRequest request) {
        return problem(request, HttpStatus.UNPROCESSABLE_CONTENT,
                "Dispatch input unavailable", "Dispatch cannot be prepared from the supplied input.");
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpServletRequest request,
            HttpStatus status,
            String title,
            String detail
    ) {
        var body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        body.setProperty("correlationId", request.getAttribute(CorrelationIdFilter.ATTRIBUTE));
        return ResponseEntity.status(status).body(body);
    }
}
