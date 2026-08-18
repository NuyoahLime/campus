package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.query.SchoolAdministrationAccessQuery;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;
import java.util.stream.Stream;

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

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findActiveSchoolAdminSchoolIds(UUID userId) {
        if (userId == null) return List.of();
        return memberships.findAllByUserIdAndStatusAndRoleInSchoolInOrderByStartedAtAscIdAsc(
                        userId, "ACTIVE", List.of("SCHOOL_ADMIN"))
                .stream().map(SchoolMembershipEntity::getSchoolId).distinct().toList();
    }
}
