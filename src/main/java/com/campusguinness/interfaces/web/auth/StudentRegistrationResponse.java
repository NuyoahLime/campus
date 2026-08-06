package com.campusguinness.interfaces.web.auth;

import java.time.Instant;
import java.util.UUID;

public record StudentRegistrationResponse(
        UUID userId,
        UUID applicationId,
        String username,
        UUID schoolId,
        String accountStatus,
        String applicationStatus,
        Instant submittedAt
) {
}
