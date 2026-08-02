package com.campusguinness.infrastructure.security;

import com.campusguinness.interfaces.web.common.validation.Utf8ByteSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Utf8ByteSize(max = 72) String password
) {
    @Override
    public String toString() {
        return "LoginRequest{username='" + username + "', password=[REDACTED]}";
    }
}
