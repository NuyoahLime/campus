package com.campusguinness.identity.application.service;

public final class PublicRegistrationUnavailableException extends RuntimeException {
    public PublicRegistrationUnavailableException() {
        super("The requested registration is unavailable.");
    }
}
