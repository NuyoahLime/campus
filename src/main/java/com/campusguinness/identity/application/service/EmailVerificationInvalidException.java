package com.campusguinness.identity.application.service;

public final class EmailVerificationInvalidException extends RuntimeException {
    public EmailVerificationInvalidException() {
        super("The email verification link is invalid or expired.");
    }
}
