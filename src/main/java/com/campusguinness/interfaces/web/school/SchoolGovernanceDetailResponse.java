package com.campusguinness.interfaces.web.school;

import com.campusguinness.school.application.query.model.SchoolGovernanceDetailResult;

import java.time.Instant;
import java.util.UUID;

public record SchoolGovernanceDetailResponse(
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
    static SchoolGovernanceDetailResponse from(SchoolGovernanceDetailResult result) {
        return new SchoolGovernanceDetailResponse(
                result.id(), result.name(), result.status(), result.internalCode(),
                result.unifiedCodeType(), result.unifiedCode(), result.schoolType(), result.region(),
                result.address(), result.contactName(), result.contactPhone(), result.contactEmail(),
                result.normalActiveSchoolAdminCount(), result.createdAt(), result.updatedAt()
        );
    }
}
