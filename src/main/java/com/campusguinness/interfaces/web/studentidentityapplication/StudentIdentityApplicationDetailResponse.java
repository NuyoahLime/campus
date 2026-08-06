package com.campusguinness.interfaces.web.studentidentityapplication;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StudentIdentityApplicationDetailResponse(
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
        UUID reviewerId,
        Instant reviewedAt,
        String reviewReason,
        int proofFileCount,
        List<String> proofFileKeys
) {
}
