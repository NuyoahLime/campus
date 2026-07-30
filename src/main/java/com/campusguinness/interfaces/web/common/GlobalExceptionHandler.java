package com.campusguinness.interfaces.web.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(com.campusguinness.score.application.exception.ScoreReviewNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleScoreReviewNotFound(
            RuntimeException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("NOT_FOUND", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler({
            com.campusguinness.score.application.exception.ScoreReviewConflictException.class,
            com.campusguinness.score.application.exception.ScoreConfigurationException.class})
    public ResponseEntity<ApiErrorResponse> handleScoreReviewConflict(
            RuntimeException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("CONFLICT", ex.getMessage(), req.getRequestURI()));
    }

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

    @ExceptionHandler(com.campusguinness.infrastructure.security.AccountProvisioningService.DuplicateUsernameException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateUsername(
            com.campusguinness.infrastructure.security.AccountProvisioningService.DuplicateUsernameException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("USERNAME_ALREADY_EXISTS", "Username already exists", req.getRequestURI()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(IllegalStateException ex, HttpServletRequest req) {
        String msg = ex.getMessage();
        if (msg != null && (msg.contains("membership") || msg.contains("No active"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiErrorResponse.of("FORBIDDEN", msg, req.getRequestURI()));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("CONFLICT", msg, req.getRequestURI()));
    }

    @ExceptionHandler(com.campusguinness.score.internal.persistence.ScoreValuePersistenceException.class)
    public ResponseEntity<ApiErrorResponse> handlePersistenceCorruption(
            com.campusguinness.score.internal.persistence.ScoreValuePersistenceException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("INTERNAL_DATA_CORRUPTION",
                        "Stored data could not be restored safely.", req.getRequestURI()));
    }

    @ExceptionHandler({org.springframework.security.authorization.AuthorizationDeniedException.class,
            org.springframework.security.access.AccessDeniedException.class})
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(Exception ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of("ACCESS_DENIED", ex.getMessage(), req.getRequestURI()));
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

    @ExceptionHandler({OptimisticLockingFailureException.class,
            ObjectOptimisticLockingFailureException.class,
            jakarta.persistence.OptimisticLockException.class,
            PessimisticLockingFailureException.class,
            DataIntegrityViolationException.class})
    public ResponseEntity<ApiErrorResponse> handleOptimisticLock(RuntimeException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("CONCURRENT_MODIFICATION",
                        "申请状态已发生变化，请刷新后重试。", req.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnknown(Exception ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred", req.getRequestURI()));
    }
}
