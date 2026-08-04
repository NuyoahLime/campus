package com.campusguinness.identity.application.service;

public record ResendVerificationResponse(String message) {
    public static ResendVerificationResponse generic() {
        return new ResendVerificationResponse(
                "If an unverified account exists, a verification email will be sent.");
    }
}
