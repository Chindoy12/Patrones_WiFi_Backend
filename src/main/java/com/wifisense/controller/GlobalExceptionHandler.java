package com.wifisense.controller;

import com.wifisense.analysis.AiServiceUnavailableException;
import com.wifisense.analysis.InsufficientDataException;
import com.wifisense.network.DataSourceUnavailableException;
import com.wifisense.network.decorator.InvalidSnapshotException;
import com.wifisense.security.InvalidEncryptedPasswordException;
import com.wifisense.security.LoginBlockedException;
import com.wifisense.security.RegistrationBlockedException;
import com.wifisense.service.DuplicateResourceException;
import com.wifisense.service.InvalidAccountException;
import com.wifisense.service.ResourceNotFoundException;
import com.wifisense.service.UserLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.TreeMap;

/** Maps exceptions to RFC 7807 responses with Spanish messages; internal details are never exposed. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail unauthorized(AuthenticationException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Datos incorrectos.");
    }

    @ExceptionHandler({LoginBlockedException.class, RegistrationBlockedException.class})
    ProblemDetail blocked(RuntimeException e) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(InvalidEncryptedPasswordException.class)
    ProblemDetail expiredForm(InvalidEncryptedPasswordException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(InvalidAccountException.class)
    ProblemDetail invalidAccount(InvalidAccountException e) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setProperty("errors", e.getErrors());
        return problem;
    }

    @ExceptionHandler({ResourceNotFoundException.class, NoResourceFoundException.class})
    ProblemDetail notFound(Exception e) {
        return problem(HttpStatus.NOT_FOUND, e instanceof ResourceNotFoundException
                ? e.getMessage() : "El recurso solicitado no existe.");
    }

    @ExceptionHandler({DuplicateResourceException.class, UserLimitException.class, IllegalStateException.class})
    ProblemDetail conflict(RuntimeException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "La operación no es válida para los datos existentes.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ProblemDetail unreadable(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "La solicitud no tiene un formato válido.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new TreeMap<>();
        e.getBindingResult().getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Revisa los datos ingresados.");
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
                ? "El servicio de IA no está disponible. Intenta más tarde o usa otro tipo de análisis." : e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        if (e instanceof ErrorResponse springError) {
            return problem(HttpStatus.valueOf(springError.getStatusCode().value()), "La solicitud no es válida.");
        }
        log.error("Unexpected error", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error interno. Intenta de nuevo.");
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
