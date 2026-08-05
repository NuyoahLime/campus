package com.campusguinness.interfaces.web.common;

import jakarta.servlet.http.HttpServletRequest;
import com.campusguinness.identity.application.exception.IdentityApplicationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        var details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ApiFieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("VALIDATION_FAILED", "Request validation failed", req.getRequestURI(), details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformed(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("MALFORMED_REQUEST", "Request body is malformed", req.getRequestURI()));
    }

    @ExceptionHandler(IdentityApplicationException.class)
    public ResponseEntity<ApiErrorResponse> handleIdentityApplication(IdentityApplicationException ex, HttpServletRequest req) {
        return ResponseEntity.status(statusFor(ex.code()))
                .body(ApiErrorResponse.of(ex.code(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(RuntimeException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of("ACCESS_DENIED", "Access is denied.", req.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        if (containsInCauseChain(ex, "uq_users_username")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiErrorResponse.of("USERNAME_ALREADY_EXISTS", "Username already exists.", req.getRequestURI()));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred", req.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(IllegalArgumentException ex, HttpServletRequest req) {
        String msg = ex.getMessage();
        if (msg != null && msg.contains("not found")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiErrorResponse.of("NOT_FOUND", msg, req.getRequestURI()));
        }
        if (msg != null && (msg.contains("already exists") || msg.contains("already disabled") || msg.contains("already enabled"))) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiErrorResponse.of("CONFLICT", msg, req.getRequestURI()));
        }
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("BAD_REQUEST", msg != null ? msg : "Invalid request", req.getRequestURI()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(IllegalStateException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("CONFLICT", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(com.campusguinness.score.internal.persistence.ScoreValuePersistenceException.class)
    public ResponseEntity<ApiErrorResponse> handlePersistenceCorruption(
            com.campusguinness.score.internal.persistence.ScoreValuePersistenceException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("INTERNAL_DATA_CORRUPTION",
                        "Stored data could not be restored safely.", req.getRequestURI()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleDomainConflict(RuntimeException ex, HttpServletRequest req) {
        String msg = ex.getMessage();
        if (msg != null && msg.contains("Cannot") && msg.contains("status")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiErrorResponse.of("CONFLICT", msg, req.getRequestURI()));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred", req.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnknown(Exception ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred", req.getRequestURI()));
    }

    private HttpStatus statusFor(String code) {
        return switch (code) {
            case "INVITATION_ACTIVATION_FAILED" -> HttpStatus.UNAUTHORIZED;
            case "INVITATION_EXPIRED" -> HttpStatus.GONE;
            case "SCHOOL_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "USERNAME_ALREADY_EXISTS",
                    "ACTIVE_INVITATION_ALREADY_EXISTS",
                    "INVITATION_NOT_PENDING",
                    "ACCOUNT_ALREADY_ACTIVATED",
                    "ACCOUNT_NOT_ACTIVATABLE",
                    "SCHOOL_ADMIN_MEMBERSHIP_CONFLICT",
                    "SCHOOL_NOT_OPEN_FOR_REGISTRATION" -> HttpStatus.CONFLICT;
            case "INVITATION_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    private boolean containsInCauseChain(Throwable ex, String text) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(text)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
