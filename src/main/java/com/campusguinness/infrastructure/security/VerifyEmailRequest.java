package com.campusguinness.infrastructure.security;

public record VerifyEmailRequest(String token) {
    @Override
    public String toString() {
        return "VerifyEmailRequest{token=[REDACTED]}";
    }
}
