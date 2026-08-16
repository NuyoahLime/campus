package com.campusguinness.school.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record SchoolGovernanceDetailResult(
        UUID id,
        String name,
        String status,
        String internalCode,
        String unifiedCodeType,
        String unifiedCode,
        String schoolType,
        String region,
        String address,
        String contactName,
        String contactPhone,
        String contactEmail,
        long normalActiveSchoolAdminCount,
        Instant createdAt,
        Instant updatedAt
) {
}
