package com.wifisense.controller;

import com.wifisense.analysis.AiServiceUnavailableException;
import com.wifisense.analysis.InsufficientDataException;
import com.wifisense.network.DataSourceUnavailableException;
import com.wifisense.network.decorator.InvalidSnapshotException;
import com.wifisense.service.DuplicateResourceException;
import com.wifisense.service.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.TreeMap;

/** Maps exceptions to RFC 7807 problem responses; internal details are never exposed. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail notFound(ResourceNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({DuplicateResourceException.class, IllegalStateException.class,
            DataIntegrityViolationException.class})
    ProblemDetail conflict(RuntimeException e) {
        String detail = e instanceof DataIntegrityViolationException
                ? "The operation violates a data constraint" : e.getMessage();
        return problem(HttpStatus.CONFLICT, detail);
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail badRequest(RuntimeException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new TreeMap<>();
        e.getBindingResult().getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler({InsufficientDataException.class, InvalidSnapshotException.class})
    ProblemDetail unprocessable(RuntimeException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler({DataSourceUnavailableException.class, AiServiceUnavailableException.class})
    ProblemDetail unavailable(RuntimeException e) {
        log.warn("Dependency unavailable: {}", e.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, e instanceof AiServiceUnavailableException
                ? "AI service is unavailable, try another analysis type or retry later" : e.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail unauthorized(AuthenticationException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
