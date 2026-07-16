package com.campusguinness.interfaces.web.common;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        String code,
        String message,
        String path,
        Instant timestamp,
        List<ApiFieldError> details) {

    public static ApiErrorResponse of(String code, String message, String path) {
        return new ApiErrorResponse(code, message, path, Instant.now(), List.of());
    }

    public static ApiErrorResponse of(String code, String message, String path, List<ApiFieldError> details) {
        return new ApiErrorResponse(code, message, path, Instant.now(), details);
    }
}
