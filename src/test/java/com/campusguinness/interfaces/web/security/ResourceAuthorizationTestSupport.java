package com.campusguinness.interfaces.web.security;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.infrastructure.security.AuthenticatedSchoolMembership;
import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

abstract class ResourceAuthorizationTestSupport extends PostgreSqlIntegrationTestSupport {

    @Autowired protected JdbcTemplate jdbc;

    protected final String runPrefix = "phase10-" + UUID.randomUUID();

    @AfterEach
    void clearSecurityContextAndData() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM feedbacks WHERE content LIKE ?", runPrefix + "%");
        jdbc.update("DELETE FROM school_memberships WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)",
                runPrefix + "%");
        jdbc.update("DELETE FROM users WHERE username LIKE ?", runPrefix + "%");
        jdbc.update("DELETE FROM schools WHERE name LIKE ?", runPrefix + "%");
    }

    protected UUID insertSchool(String label) {
        UUID id = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String compactCode = "p10-" + suffix;
        jdbc.update("""
                INSERT INTO schools(
                    id, name, unified_code_type, unified_code, internal_code, school_type, region,
                    address, contact_name, contact_phone, contact_email, school_status
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id, runPrefix + "-" + label, "USCC", compactCode + "-uc",
                compactCode + "-ic", "PRIMARY", "Beijing", "Address", "Contact",
                "13800000000", "phase10@example.com", "NORMAL");
        return id;
    }

    protected UUID insertUser(String label) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, username, password_hash, account_status) VALUES (?,?,?,?)",
                id, runPrefix + "-" + label + "-" + UUID.randomUUID().toString().substring(0, 8),
                "{noop}password", "NORMAL");
        return id;
    }

    protected UUID insertMembership(UUID userId, UUID schoolId, String role, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO school_memberships(id, user_id, school_id, role_in_school, status)
                VALUES (?, ?, ?, ?, ?)
                """, id, userId, schoolId, role, status);
        return id;
    }

    protected UUID insertFeedback(UUID schoolId, UUID submitterId, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO feedbacks(id, school_id, submitter_id, feedback_type, content, feedback_status, version)
                VALUES (?, ?, ?, 'GENERAL', ?, ?, 0)
                """, id, schoolId, submitterId, runPrefix + "-feedback-" + id, status);
        return id;
    }

    protected void authenticate(UUID userId, String role, List<AuthenticatedSchoolMembership> memberships) {
        var details = new CampusGuinnessUserDetails(
                userId,
                runPrefix + "-principal",
                "{noop}password",
                "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_" + role)),
                memberships);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, "n/a", details.getAuthorities()));
    }

    protected AuthenticatedSchoolMembership snapshotMembership(UUID membershipId, UUID schoolId, String role) {
        return new AuthenticatedSchoolMembership(membershipId, schoolId, role);
    }
}
