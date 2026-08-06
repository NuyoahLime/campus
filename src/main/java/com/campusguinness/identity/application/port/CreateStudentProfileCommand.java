package com.campusguinness.identity.application.port;

import java.util.UUID;

public record CreateStudentProfileCommand(
        UUID profileId,
        UUID membershipId,
        String grade,
        String className,
        String studentNumber
) {
}
