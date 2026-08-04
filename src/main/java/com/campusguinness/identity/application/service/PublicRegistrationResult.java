package com.campusguinness.identity.application.service;

public record PublicRegistrationResult(
        String username,
        boolean verificationRequired,
        String nextAction
) {
    public static PublicRegistrationResult verifyEmail(String username) {
        return new PublicRegistrationResult(username, true, "VERIFY_EMAIL");
    }
}
