package com.campusguinness.school.application.query.model;

import java.util.UUID;

public record StudentRegistrationSchool(
        UUID schoolId,
        boolean exists,
        boolean openForStudentRegistration
) {
}
