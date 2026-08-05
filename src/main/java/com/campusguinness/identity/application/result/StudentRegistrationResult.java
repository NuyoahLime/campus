package com.campusguinness.identity.application.result;

import com.campusguinness.identity.internal.domain.AccountStatus;
import com.campusguinness.identity.internal.domain.StudentIdentityApplicationStatus;

import java.time.Instant;
import java.util.UUID;

public record StudentRegistrationResult(
        UUID userId,
        UUID applicationId,
        String username,
        UUID schoolId,
        AccountStatus accountStatus,
        StudentIdentityApplicationStatus applicationStatus,
        Instant submittedAt
) {
}
