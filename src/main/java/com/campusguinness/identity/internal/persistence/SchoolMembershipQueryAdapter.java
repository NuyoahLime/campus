package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
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

    @Override
    public boolean hasActiveSchoolAdminMembership(UUID userId, UUID schoolId) {
        return jpa.findByUserIdAndSchoolIdAndRoleInSchoolAndStatus(
                userId, schoolId, "SCHOOL_ADMIN", "ACTIVE").isPresent();
    }

    @Override
    public Optional<UUID> findActiveSchoolAdminSchoolId(UUID userId) {
        var memberships = jpa.findByUserIdAndRoleInSchoolAndStatus(
                userId, "SCHOOL_ADMIN", "ACTIVE");
        return memberships.stream()
                .findFirst()
                .map(SchoolMembershipEntity::getSchoolId);
    }

    @Override
    public Optional<UUID> findActiveTeacherMembershipId(UUID userId, UUID schoolId) {
        return jpa.findByUserIdAndSchoolIdAndRoleInSchoolAndStatus(
                userId, schoolId, "TEACHER", "ACTIVE")
                .map(SchoolMembershipEntity::getId);
    }
}
