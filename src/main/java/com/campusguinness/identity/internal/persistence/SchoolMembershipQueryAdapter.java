package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@Transactional(readOnly = true)
class SchoolMembershipQueryAdapter implements SchoolMembershipQueryPort {

    private final SchoolMembershipJpaRepository jpa;

    SchoolMembershipQueryAdapter(SchoolMembershipJpaRepository jpa) { this.jpa = jpa; }

    @Override
    public boolean hasActiveTeacherMembership(UUID userId, UUID schoolId) {
        return jpa.findByUserIdAndSchoolIdAndRoleInSchoolAndStatus(
                userId, schoolId, "TEACHER", "ACTIVE").isPresent();
    }
}
