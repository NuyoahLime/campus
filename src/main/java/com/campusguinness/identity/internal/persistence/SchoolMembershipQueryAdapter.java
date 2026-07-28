package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
class SchoolMembershipQueryAdapter implements SchoolMembershipQueryPort {

    private final SchoolMembershipJpaRepository jpa;
    private final JdbcTemplate jdbc;

    SchoolMembershipQueryAdapter(SchoolMembershipJpaRepository jpa, JdbcTemplate jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
    }

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
    public boolean hasActiveStudentMembership(UUID userId, UUID schoolId) {
        return jpa.findByUserIdAndSchoolIdAndRoleInSchoolAndStatus(
                userId, schoolId, "STUDENT", "ACTIVE").isPresent();
    }

    @Override
    public Optional<UUID> findActiveSchoolAdminSchoolId(UUID userId) {
        return jpa.findByUserIdAndRoleInSchoolAndStatus(userId, "SCHOOL_ADMIN", "ACTIVE")
                .stream().findFirst().map(SchoolMembershipEntity::getSchoolId);
    }

    @Override
    public Optional<UUID> findActiveTeacherMembershipId(UUID userId, UUID schoolId) {
        return jpa.findByUserIdAndSchoolIdAndRoleInSchoolAndStatus(
                userId, schoolId, "TEACHER", "ACTIVE").map(SchoolMembershipEntity::getId);
    }

    @Override
    public Optional<UUID> findActiveStudentMembershipId(UUID userId, UUID schoolId) {
        return jpa.findByUserIdAndSchoolIdAndRoleInSchoolAndStatus(
                userId, schoolId, "STUDENT", "ACTIVE").map(SchoolMembershipEntity::getId);
    }

    @Override
    public List<UUID> findActiveStudentMembershipIds(UUID userId) {
        return jpa.findByUserIdAndRoleInSchoolAndStatus(userId, "STUDENT", "ACTIVE")
                .stream().map(SchoolMembershipEntity::getId).toList();
    }

    @Override
    public Map<UUID, UUID> findUserIdsByMembershipIds(List<UUID> membershipIds) {
        if (membershipIds.isEmpty()) return Map.of();
        return jpa.findAllById(membershipIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        SchoolMembershipEntity::getId, SchoolMembershipEntity::getUserId));
    }

    @Override
    public boolean hasNormalAccountStatus(UUID userId) {
        var rows = jdbc.queryForList(
                "SELECT account_status FROM users WHERE id = ?", String.class, userId);
        return !rows.isEmpty() && "NORMAL".equals(rows.getFirst());
    }
}
