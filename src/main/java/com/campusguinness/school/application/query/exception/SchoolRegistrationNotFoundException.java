package com.campusguinness.school.application.query.exception;

import java.util.UUID;

public class SchoolRegistrationNotFoundException extends RuntimeException {

    public SchoolRegistrationNotFoundException(UUID registrationId) {
        super("School registration not found: " + registrationId);
    }
}
