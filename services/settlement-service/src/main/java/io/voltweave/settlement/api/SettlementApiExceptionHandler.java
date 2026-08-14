package io.voltweave.settlement.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.voltweave.settlement.exception.IdempotencyConflictException;

@RestControllerAdvice
public class SettlementApiExceptionHandler {
    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<ProblemDetail> conflict() {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "The Idempotency-Key was already used with another request."
        );
        problem.setTitle("Idempotency conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<ProblemDetail> badRequest() {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request is invalid."
        );
        problem.setTitle("Invalid request");
        return ResponseEntity.badRequest().body(problem);
    }
}
