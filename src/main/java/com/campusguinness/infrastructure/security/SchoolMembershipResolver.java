package com.campusguinness.infrastructure.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only resolver for school membership authorization checks.
 * Queries existing school_memberships table (V003).
 * School roles are NOT loaded into GrantedAuthority — they are checked
 * dynamically against the target school for each resource operation.
 */
@Component
@Transactional(readOnly = true)
public class SchoolMembershipResolver {

    private final JdbcTemplate jdbc;

    public SchoolMembershipResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns the roleInSchool if the user has an ACTIVE membership at the given school. */
    public Optional<String> resolveRole(UUID userId, UUID schoolId) {
        var rows = jdbc.queryForList(
                "SELECT role_in_school FROM school_memberships " +
                        "WHERE user_id = ? AND school_id = ? AND status = 'ACTIVE'",
                String.class, userId, schoolId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    /** True if the user is a SCHOOL_ADMIN at the given school. */
    public boolean isSchoolAdmin(UUID userId, UUID schoolId) {
        return "SCHOOL_ADMIN".equals(resolveRole(userId, schoolId).orElse(null));
    }

    /** True if the user is a TEACHER or SCHOOL_ADMIN at the given school. */
    public boolean isTeacherOrAbove(UUID userId, UUID schoolId) {
        String role = resolveRole(userId, schoolId).orElse(null);
        return "TEACHER".equals(role) || "SCHOOL_ADMIN".equals(role);
    }
}
