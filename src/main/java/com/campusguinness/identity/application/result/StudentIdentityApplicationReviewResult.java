package com.campusguinness.identity.application.result;

import java.time.Instant;
import java.util.UUID;

public record StudentIdentityApplicationReviewResult(
        UUID applicationId,
        UUID userId,
        UUID schoolId,
        String applicationStatus,
        String accountStatus,
        String membershipRole,
        String membershipStatus,
        String reason,
        Instant reviewedAt
) {
}
