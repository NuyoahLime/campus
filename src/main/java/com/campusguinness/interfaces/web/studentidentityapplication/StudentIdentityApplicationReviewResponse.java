package com.campusguinness.interfaces.web.studentidentityapplication;

import java.time.Instant;
import java.util.UUID;

public record StudentIdentityApplicationReviewResponse(
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
