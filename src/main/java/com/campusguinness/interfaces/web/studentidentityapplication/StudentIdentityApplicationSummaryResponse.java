package com.campusguinness.interfaces.web.studentidentityapplication;

import java.time.Instant;
import java.util.UUID;

public record StudentIdentityApplicationSummaryResponse(
        UUID applicationId,
        UUID userId,
        UUID schoolId,
        String username,
        String realName,
        String studentNumber,
        String grade,
        String className,
        String applicationStatus,
        Instant submittedAt,
        Instant reviewedAt
) {
}
