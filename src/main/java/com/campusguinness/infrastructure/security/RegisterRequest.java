package com.campusguinness.infrastructure.security;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank String password,
        @NotBlank String confirmPassword
) {
    @Override
    public String toString() {
        return "RegisterRequest{username='" + username + "', email='" + email
                + "', password=[REDACTED], confirmPassword=[REDACTED]}";
    }
}
