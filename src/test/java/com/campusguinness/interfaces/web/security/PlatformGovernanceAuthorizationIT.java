package com.campusguinness.interfaces.web.security;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.infrastructure.security.AuthenticatedSchoolMembership;
import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "campus-guinness.security.cors.allowed-origins=http://localhost:5173")
class PlatformGovernanceAuthorizationIT extends PostgreSqlIntegrationTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private final String runPrefix = "phase10-platform-" + UUID.randomUUID();
    private UUID schoolId;
    private UUID superAdminId;
    private UUID schoolAdminId;
    private UUID studentId;

    @BeforeEach
    void setUp() {
        schoolId = insertSchool();
        superAdminId = insertUser("super-admin", "NORMAL", "SUPER_ADMIN");
        schoolAdminId = insertUser("school-admin", "NORMAL", null);
        studentId = insertUser("student", "NORMAL", null);
        insertMembership(schoolAdminId, schoolId, "SCHOOL_ADMIN", "ACTIVE");
        insertMembership(studentId, schoolId, "STUDENT", "ACTIVE");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM school_registrations WHERE school_name LIKE ?", runPrefix + "%");
        jdbc.update("DELETE FROM school_memberships WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)",
                runPrefix + "%");
        jdbc.update("DELETE FROM users WHERE username LIKE ?", runPrefix + "%");
        jdbc.update("DELETE FROM schools WHERE name LIKE ?", runPrefix + "%");
    }

    @Test
    void authoritativeSuperAdminCanReadSchoolGovernanceResource() throws Exception {
        mvc.perform(get("/api/v1/schools/{id}", schoolId).with(principal(superAdminId, "SUPER_ADMIN", List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(schoolId.toString()));

        mvc.perform(get("/api/v1/schools/governance")
                        .with(principal(superAdminId, "SUPER_ADMIN", List.of())))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/schools/{id}/school-admins", schoolId)
                        .with(principal(superAdminId, "SUPER_ADMIN", List.of())))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/schools/{id}/school-admin-invitations", schoolId)
                        .with(principal(superAdminId, "SUPER_ADMIN", List.of())))
                .andExpect(status().isOk());
    }

    @Test
    void schoolAdminStudentAndAnonymousCannotReadSchoolGovernanceResource() throws Exception {
        mvc.perform(get("/api/v1/schools/{id}", schoolId)
                        .with(principal(schoolAdminId, "SCHOOL_ADMIN", memberships(schoolAdminId, "SCHOOL_ADMIN"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/schools/{id}", schoolId)
                        .with(principal(studentId, "STUDENT", memberships(studentId, "STUDENT"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/schools/{id}", schoolId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void forgedSuperAdminAuthorityCannotBypassAuthoritativeIdentity() throws Exception {
        String forgedUsername = runPrefix + "-forged-user";

        mvc.perform(post("/api/v1/users")
                        .with(principal(studentId, "SUPER_ADMIN", memberships(studentId, "STUDENT")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","initialPassword":"Password123!"}
                                """.formatted(forgedUsername)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLATFORM_GOVERNANCE_DENIED"));

        assertThat(countUsers(forgedUsername)).isZero();

        mvc.perform(get("/api/v1/schools/governance")
                        .with(principal(studentId, "SUPER_ADMIN", memberships(studentId, "STUDENT"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLATFORM_GOVERNANCE_DENIED"));
    }

    @Test
    void staleSuperAdminIdentityWithActiveSchoolMembershipIsDenied() throws Exception {
        UUID membershipId = insertMembership(superAdminId, schoolId, "SCHOOL_ADMIN", "ACTIVE");

        mvc.perform(get("/api/v1/schools/{id}", schoolId)
                        .with(principal(superAdminId, "SUPER_ADMIN", List.of(
                                new AuthenticatedSchoolMembership(membershipId, schoolId, "SCHOOL_ADMIN")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLATFORM_GOVERNANCE_DENIED"));
    }

    @Test
    void schoolRegistrationReviewerComesFromAuthenticatedPrincipal() throws Exception {
        UUID registrationId = insertSchoolRegistration();
        UUID spoofedReviewerId = UUID.randomUUID();

        mvc.perform(post("/api/v1/school-registrations/{id}/approve", registrationId)
                        .with(principal(superAdminId, "SUPER_ADMIN", List.of()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerId":"%s",
                                  "actorId":"%s",
                                  "schoolId":"%s",
                                  "comment":"approved"
                                }
                                """.formatted(spoofedReviewerId, UUID.randomUUID(), schoolId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(registrationReviewer(registrationId)).isEqualTo(superAdminId);
        assertThat(registrationReviewer(registrationId)).isNotEqualTo(spoofedReviewerId);
    }

    private RequestPostProcessor principal(
            UUID userId,
            String role,
            List<AuthenticatedSchoolMembership> memberships
    ) {
        var details = new CampusGuinnessUserDetails(
                userId,
                runPrefix + "-principal",
                "{noop}password",
                "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_" + role)),
                memberships
        );
        return user(details);
    }

    private List<AuthenticatedSchoolMembership> memberships(UUID userId, String role) {
        UUID membershipId = jdbc.queryForObject(
                "SELECT id FROM school_memberships WHERE user_id = ? AND status = 'ACTIVE'",
                UUID.class,
                userId
        );
        return List.of(new AuthenticatedSchoolMembership(membershipId, schoolId, role));
    }

    private UUID insertSchool() {
        UUID id = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO schools(
                    id, name, unified_code_type, unified_code, internal_code, school_type, region,
                    address, contact_name, contact_phone, contact_email, school_status
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id, runPrefix + "-school", "USCC", "phase10-uc-" + suffix,
                "phase10-ic-" + suffix, "PRIMARY", "Beijing", "Address", "Contact",
                "13800000000", "phase10@example.com", "NORMAL");
        return id;
    }

    private UUID insertUser(String label, String status, String platformRole) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, username, password_hash, account_status, platform_role) VALUES (?,?,?,?,?)",
                id, runPrefix + "-" + label, "{noop}password", status, platformRole);
        return id;
    }

    private UUID insertMembership(UUID userId, UUID schoolId, String role, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO school_memberships(id, user_id, school_id, role_in_school, status)
                VALUES (?, ?, ?, ?, ?)
                """, id, userId, schoolId, role, status);
        return id;
    }

    private UUID insertSchoolRegistration() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO school_registrations(
                    id, school_name, unified_code_type, school_type, region, address,
                    contact_name, contact_phone, contact_email, registration_status, version
                ) VALUES (?, ?, 'USCC', 'PRIMARY', 'Beijing', 'Address', 'Contact',
                          '13800000000', 'phase10@example.com', 'SUBMITTED', 0)
                """, id, runPrefix + "-registration");
        return id;
    }

    private int countUsers(String username) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, username);
    }

    private UUID registrationReviewer(UUID registrationId) {
        return jdbc.queryForObject(
                "SELECT reviewed_by FROM school_registrations WHERE id = ?",
                UUID.class,
                registrationId
        );
    }
}
