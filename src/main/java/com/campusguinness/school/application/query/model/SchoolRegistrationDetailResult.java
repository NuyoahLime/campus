package com.campusguinness.school.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record SchoolRegistrationDetailResult(
        UUID id,
        String schoolName,
        String unifiedCodeType,
        String unifiedCode,
        String schoolType,
        String region,
        String address,
        String contactName,
        String contactPhone,
        String contactEmail,
        String description,
        boolean evidenceSubmitted,
        String status,
        UUID createdSchoolId,
        UUID reviewedBy,
        Instant reviewedAt,
        String reviewComment,
        String rejectReason,
        Instant createdAt,
        Instant updatedAt
) {}
