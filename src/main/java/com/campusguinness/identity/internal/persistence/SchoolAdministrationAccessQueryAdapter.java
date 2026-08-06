package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.query.SchoolAdministrationAccessQuery;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
class SchoolAdministrationAccessQueryAdapter implements SchoolAdministrationAccessQuery {

    private final SchoolMembershipJpaRepository memberships;

    SchoolAdministrationAccessQueryAdapter(SchoolMembershipJpaRepository memberships) {
        this.memberships = memberships;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveSchoolAdminMembership(UUID userId, UUID schoolId) {
        return userId != null && schoolId != null
                && memberships.existsByUserIdAndSchoolIdAndStatusAndRoleInSchool(
                userId, schoolId, "ACTIVE", "SCHOOL_ADMIN");
    }
}
