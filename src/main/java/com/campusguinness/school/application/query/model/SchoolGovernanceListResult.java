package com.campusguinness.school.application.query.model;

import java.util.UUID;

public record SchoolGovernanceListResult(
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
}
