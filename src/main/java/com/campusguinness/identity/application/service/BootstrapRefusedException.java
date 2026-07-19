package com.campusguinness.identity.application.service;

/**
 * Thrown when SUPER_ADMIN bootstrap is refused (e.g. database not empty).
 */
public final class BootstrapRefusedException extends RuntimeException {

    public BootstrapRefusedException(String message) {
        super(message);
    }
}
