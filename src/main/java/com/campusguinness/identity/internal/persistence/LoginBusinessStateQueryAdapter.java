package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.query.LoginBusinessStateQuery;
import com.campusguinness.identity.application.query.LatestStudentIdentityApplicationState;
import com.campusguinness.identity.application.query.SchoolAdminInvitationLoginState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
class LoginBusinessStateQueryAdapter implements LoginBusinessStateQuery {

    private final JdbcTemplate jdbc;

    LoginBusinessStateQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LatestStudentIdentityApplicationState> findLatestStudentApplication(UUID userId) {
        if (userId == null) return Optional.empty();
        return jdbc.query("""
                SELECT id, application_status, created_at
                FROM student_identity_applications
                WHERE user_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """, rs -> rs.next()
                ? Optional.of(new LatestStudentIdentityApplicationState(
                        (UUID) rs.getObject("id"),
                        rs.getString("application_status"),
                        rs.getTimestamp("created_at").toInstant()
                ))
                : Optional.empty(), userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SchoolAdminInvitationLoginState> findLatestSchoolAdminInvitation(UUID userId) {
        if (userId == null) return Optional.empty();
        return jdbc.query("""
                SELECT id, school_id, role_in_school, invitation_status, expires_at
                FROM school_admin_invitations
                WHERE user_id = ? AND role_in_school = 'SCHOOL_ADMIN'
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """, rs -> rs.next()
                ? Optional.of(new SchoolAdminInvitationLoginState(
                        (UUID) rs.getObject("id"),
                        (UUID) rs.getObject("school_id"),
                        rs.getString("role_in_school"),
                        rs.getString("invitation_status"),
                        rs.getTimestamp("expires_at").toInstant()
                ))
                : Optional.empty(), userId);
    }
}
