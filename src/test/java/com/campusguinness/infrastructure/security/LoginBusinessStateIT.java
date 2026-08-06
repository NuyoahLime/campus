package com.campusguinness.infrastructure.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginBusinessStateIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    private static final String PASSWORD = "testPass123";
    private final String prefix = "phase7-" + UUID.randomUUID().toString().substring(0, 8);

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM school_admin_invitations WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)",
                prefix + "%");
        jdbc.update("DELETE FROM student_identity_applications WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)",
                prefix + "%");
        jdbc.update("DELETE FROM school_memberships WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)",
                prefix + "%");
        jdbc.update("DELETE FROM users WHERE username LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM schools WHERE internal_code LIKE ?", prefix + "%");
    }

    @Test
    void pendingStudentWrongPasswordIsAuthenticationFailedAndCorrectPasswordExplainsPending() throws Exception {
        UUID schoolId = createSchool();
        UUID userId = createUser("student-pending", "PENDING_ACTIVATION", null);
        createStudentApplication(userId, schoolId, "PENDING");

        login(username("student-pending"), "wrong")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));

        assertThat(loginFailures(userId)).isZero();

        login(username("student-pending"), PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_APPROVAL_PENDING"));
    }

    @Test
    void rejectedStudentCorrectPasswordExplainsRejection() throws Exception {
        UUID schoolId = createSchool();
        UUID userId = createUser("student-rejected", "PENDING_ACTIVATION", null);
        createStudentApplication(userId, schoolId, "REJECTED");

        login(username("student-rejected"), PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_APPLICATION_REJECTED"));
    }

    @Test
    void pendingSchoolAdminInvitationCorrectPasswordExplainsActivationPending() throws Exception {
        UUID schoolId = createSchool();
        UUID userId = createUser("admin-pending", "PENDING_ACTIVATION", null);
        UUID creatorId = createUser("super-for-invite", "NORMAL", "SUPER_ADMIN");
        jdbc.update("""
                INSERT INTO school_admin_invitations(
                    id, user_id, school_id, role_in_school, invitation_code_hash,
                    invitation_status, expires_at, created_by)
                VALUES (?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), userId, schoolId, "SCHOOL_ADMIN", "hash",
                "PENDING", Timestamp.from(Instant.now().plusSeconds(3600)), creatorId);

        login(username("admin-pending"), "wrong")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));

        login(username("admin-pending"), PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCHOOL_ADMIN_ACTIVATION_PENDING"));
    }

    @Test
    void disabledAndLockedAreOnlyRevealedAfterCorrectPassword() throws Exception {
        createUser("disabled", "DISABLED", null);
        createUser("locked", "LOCKED", null);

        login(username("disabled"), "wrong")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
        login(username("disabled"), PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));

        login(username("locked"), "wrong")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
        login(username("locked"), PASSWORD)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void formalRolesLoadActiveMembershipsWithMembershipIdsAndIgnoreTeacher() throws Exception {
        UUID studentSchool = createSchool();
        UUID adminSchool = createSchool();
        UUID studentUser = createUser("student-normal", "NORMAL", null);
        UUID adminUser = createUser("school-admin-normal", "NORMAL", null);
        UUID teacherOnlyUser = createUser("teacher-only", "NORMAL", null);
        UUID studentMembership = createMembership(studentUser, studentSchool, "STUDENT", "ACTIVE");
        UUID adminMembership = createMembership(adminUser, adminSchool, "SCHOOL_ADMIN", "ACTIVE");
        createMembership(teacherOnlyUser, createSchool(), "TEACHER", "ACTIVE");

        login(username("student-normal"), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_STUDENT"))
                .andExpect(jsonPath("$.schoolMemberships[0].membershipId").value(studentMembership.toString()))
                .andExpect(jsonPath("$.schoolMemberships[0].schoolId").value(studentSchool.toString()))
                .andExpect(jsonPath("$.schoolMemberships[0].roleInSchool").value("STUDENT"));

        login(username("school-admin-normal"), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_SCHOOL_ADMIN"))
                .andExpect(jsonPath("$.schoolMemberships[0].membershipId").value(adminMembership.toString()))
                .andExpect(jsonPath("$.schoolMemberships[0].schoolId").value(adminSchool.toString()))
                .andExpect(jsonPath("$.schoolMemberships[0].roleInSchool").value("SCHOOL_ADMIN"));

        login(username("teacher-only"), PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ROLE_NOT_READY"));
    }

    private org.springframework.test.web.servlet.ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    private UUID createUser(String suffix, String status, String platformRole) {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, username, password_hash, account_status, platform_role) VALUES (?,?,?,?,?)",
                userId, username(suffix), encoder.encode(PASSWORD), status, platformRole);
        return userId;
    }

    private UUID createSchool() {
        UUID id = UUID.randomUUID();
        String code = prefix + "-" + id.toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO schools(
                    id, name, unified_code_type, unified_code, internal_code, school_type,
                    region, address, contact_name, contact_phone, contact_email, school_status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, "Phase 7 School " + code, "USCC", "U-" + code, code, "PRIMARY",
                "Region", "Address", "Contact", "13800000000", code + "@example.com", "NORMAL");
        return id;
    }

    private UUID createMembership(UUID userId, UUID schoolId, String role, String status) {
        UUID membershipId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO school_memberships(id, user_id, school_id, role_in_school, status)
                VALUES (?,?,?,?,?)
                """, membershipId, userId, schoolId, role, status);
        return membershipId;
    }

    private void createStudentApplication(UUID userId, UUID schoolId, String status) {
        UUID reviewer = "PENDING".equals(status) ? null : createUser("reviewer-" + UUID.randomUUID(), "NORMAL", "SUPER_ADMIN");
        jdbc.update("""
                INSERT INTO student_identity_applications(
                    id, user_id, school_id, real_name, student_number, grade, class_name,
                    application_status, reviewed_by, reviewed_at, rejection_reason)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), userId, schoolId, "Student", "S-" + userId.toString().substring(0, 8),
                "G1", "C1", status, reviewer, reviewer == null ? null : Timestamp.from(Instant.now()),
                "REJECTED".equals(status) ? "not valid" : null);
    }

    private int loginFailures(UUID userId) {
        return jdbc.queryForObject("SELECT login_failures FROM users WHERE id = ?", Integer.class, userId);
    }

    private String username(String suffix) {
        return prefix + "-" + suffix;
    }
}
