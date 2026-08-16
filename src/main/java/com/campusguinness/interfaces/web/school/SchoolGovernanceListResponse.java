package com.campusguinness.interfaces.web.school;

import com.campusguinness.school.application.query.model.SchoolGovernanceListResult;

import java.util.UUID;

public record SchoolGovernanceListResponse(
        UUID id,
        String name,
        String status,
        String schoolType,
        String region,
        String internalCode,
        String unifiedCodeType,
        String unifiedCode,
        long normalActiveSchoolAdminCount
) {
    static SchoolGovernanceListResponse from(SchoolGovernanceListResult result) {
        return new SchoolGovernanceListResponse(
                result.id(), result.name(), result.status(), result.schoolType(), result.region(),
                result.internalCode(), result.unifiedCodeType(), result.unifiedCode(),
                result.normalActiveSchoolAdminCount()
        );
    }
}
