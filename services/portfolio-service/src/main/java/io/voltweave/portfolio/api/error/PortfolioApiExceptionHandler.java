package io.voltweave.portfolio.api.error;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.voltweave.portfolio.http.CorrelationIdFilter;
import io.voltweave.portfolio.organization.application.exception.OrganizationNotFoundException;
import io.voltweave.portfolio.site.application.exception.SiteNotFoundException;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class PortfolioApiExceptionHandler {
    @ExceptionHandler(OrganizationNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Organization not found", request);
    }

    @ExceptionHandler(SiteNotFoundException.class)
    ResponseEntity<ProblemDetail> siteNotFound(HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Site not found", request);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    ResponseEntity<ProblemDetail> badRequest(HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict(HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Resource already exists", request);
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String title,
            HttpServletRequest request
    ) {
        var body = ProblemDetail.forStatusAndDetail(status, title);
        body.setTitle(title);
        body.setProperty(
                "correlationId",
                request.getAttribute(CorrelationIdFilter.ATTRIBUTE)
        );
        return ResponseEntity.status(status).body(body);
    }
}
