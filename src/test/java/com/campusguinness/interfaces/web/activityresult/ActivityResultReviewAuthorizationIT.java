package com.campusguinness.interfaces.web.activityresult;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.infrastructure.security.AuthenticatedSchoolMembership;
import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
class ActivityResultReviewAuthorizationIT extends PostgreSqlIntegrationTestSupport {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    private final String prefix = "slice-c-http-" + UUID.randomUUID();
    private UUID schoolA;
    private UUID schoolB;
    private UUID adminA;
    private UUID adminB;
    private UUID studentA;
    private UUID superAdmin;
    private UUID adminAMembership;
    private UUID adminBMembership;
    private UUID studentMembership;

    @BeforeEach
    void setUp() {
        schoolA = insertSchool("a");
        schoolB = insertSchool("b");
        adminA = insertUser("admin-a", null);
        adminB = insertUser("admin-b", null);
        studentA = insertUser("student-a", null);
        superAdmin = insertUser("super", "SUPER_ADMIN");
        adminAMembership = insertMembership(adminA, schoolA, "SCHOOL_ADMIN");
        adminBMembership = insertMembership(adminB, schoolB, "SCHOOL_ADMIN");
        studentMembership = insertMembership(studentA, schoolA, "STUDENT");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM result_review_records WHERE result_id IN "
                + "(SELECT id FROM activity_results WHERE school_id IN (?, ?))", schoolA, schoolB);
        jdbc.update("UPDATE activity_results SET current_candidate_version_id = NULL, "
                + "current_internal_version_id = NULL, current_public_version_id = NULL "
                + "WHERE school_id IN (?, ?)", schoolA, schoolB);
        jdbc.update("DELETE FROM result_versions WHERE result_id IN "
                + "(SELECT id FROM activity_results WHERE school_id IN (?, ?))", schoolA, schoolB);
        jdbc.update("DELETE FROM activity_results WHERE school_id IN (?, ?)", schoolA, schoolB);
        jdbc.update("DELETE FROM activities WHERE school_id IN (?, ?)", schoolA, schoolB);
        jdbc.update("DELETE FROM school_memberships WHERE user_id IN (?, ?, ?)", adminA, adminB, studentA);
        jdbc.update("DELETE FROM schools WHERE id IN (?, ?)", schoolA, schoolB);
        jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?, ?)", adminA, adminB, studentA, superAdmin);
    }

    @Test
    void submitEndpointRequiresCsrfSchoolAdminRoleAndSameSchoolAuthority() throws Exception {
        Fixture fixture = insertPublishedResult();

        mvc.perform(post("/api/v1/activity-results/{id}/submit-public-review", fixture.resultId()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/activity-results/{id}/submit-public-review", fixture.resultId())
                        .with(principal(adminA, "SCHOOL_ADMIN", adminAMembership, schoolA, "SCHOOL_ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/activity-results/{id}/submit-public-review", fixture.resultId())
                        .with(principal(studentA, "STUDENT", studentMembership, schoolA, "STUDENT"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/activity-results/{id}/submit-public-review", fixture.resultId())
                        .with(principal(superAdmin, "SUPER_ADMIN", null, null, null))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/activity-results/{id}/submit-public-review", fixture.resultId())
                        .with(principal(adminB, "SCHOOL_ADMIN", adminBMembership, schoolB, "SCHOOL_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCHOOL_ADMIN_SCOPE_DENIED"));

        mvc.perform(post("/api/v1/activity-results/{id}/submit-public-review", fixture.resultId())
                        .with(principal(adminA, "SCHOOL_ADMIN", adminAMembership, schoolA, "SCHOOL_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicStatus").value("PENDING_PUBLIC_REVIEW"));

        assertThat(reviewCount(fixture.resultId(), "SUBMITTED")).isOne();
    }

    @Test
    void reviewEndpointsAreSuperAdminOnlyAndCsrfProtected() throws Exception {
        Fixture fixture = insertPublishedResult();
        submit(fixture.resultId());

        mvc.perform(get("/api/v1/super-admin/activity-results/public-reviews"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/super-admin/activity-results/public-reviews")
                        .with(principal(adminA, "SCHOOL_ADMIN", adminAMembership, schoolA, "SCHOOL_ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/super-admin/activity-results/public-reviews")
                        .with(principal(studentA, "STUDENT", studentMembership, schoolA, "STUDENT")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/super-admin/activity-results/public-reviews")
                        .with(principal(superAdmin, "SUPER_ADMIN", null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].resultId").value(fixture.resultId().toString()));
        mvc.perform(get("/api/v1/super-admin/activity-results/{id}/public-review", fixture.resultId())
                        .with(principal(superAdmin, "SUPER_ADMIN", null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateVersionId").value(fixture.versionId().toString()));

        mvc.perform(post("/api/v1/super-admin/activity-results/{id}/approve-public-review", fixture.resultId())
                        .with(principal(superAdmin, "SUPER_ADMIN", null, null, null)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/super-admin/activity-results/{id}/approve-public-review", fixture.resultId())
                        .with(principal(adminA, "SCHOOL_ADMIN", adminAMembership, schoolA, "SCHOOL_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/super-admin/activity-results/{id}/approve-public-review", fixture.resultId())
                        .with(principal(superAdmin, "SUPER_ADMIN", null, null, null))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicStatus").value("PLATFORM_APPROVED"));
        assertThat(reviewCount(fixture.resultId(), "APPROVED")).isOne();
    }

    @Test
    void rejectEndpointIsSuperAdminOnlyAndRequiresCsrf() throws Exception {
        Fixture fixture = insertPublishedResult();
        submit(fixture.resultId());
        String path = "/api/v1/super-admin/activity-results/{id}/reject-public-review";

        mvc.perform(post(path, fixture.resultId())
                        .contentType("application/json")
                        .content("{\"reason\":\"not ready\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(path, fixture.resultId())
                        .with(principal(adminA, "SCHOOL_ADMIN", adminAMembership, schoolA, "SCHOOL_ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"reason\":\"not ready\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(path, fixture.resultId())
                        .with(principal(studentA, "STUDENT", studentMembership, schoolA, "STUDENT"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"reason\":\"not ready\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(path, fixture.resultId())
                        .with(principal(superAdmin, "SUPER_ADMIN", null, null, null))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"reason\":\"  not ready  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicStatus").value("PLATFORM_REJECTED"));

        assertThat(reviewCount(fixture.resultId(), "REJECTED")).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT reason FROM result_review_records
                WHERE result_id = ? AND action = 'REJECTED'
                """, String.class, fixture.resultId())).isEqualTo("not ready");
    }

    @Test
    void makePublicEndpointRequiresCsrfSameSchoolAdminAndApprovedCandidate() throws Exception {
        Fixture fixture = insertPublishedResult();
        submit(fixture.resultId());
        mvc.perform(post("/api/v1/super-admin/activity-results/{id}/approve-public-review", fixture.resultId())
                        .with(principal(superAdmin, "SUPER_ADMIN", null, null, null))
                        .with(csrf()))
                .andExpect(status().isOk());
        String path = "/api/v1/activity-results/{id}/make-public";

        mvc.perform(post(path, fixture.resultId()).with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path, fixture.resultId())
                        .with(principal(adminA, "SCHOOL_ADMIN", adminAMembership, schoolA, "SCHOOL_ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(post(path, fixture.resultId())
                        .with(principal(studentA, "STUDENT", studentMembership, schoolA, "STUDENT"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post(path, fixture.resultId())
                        .with(principal(superAdmin, "SUPER_ADMIN", null, null, null))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post(path, fixture.resultId())
                        .with(principal(adminB, "SCHOOL_ADMIN", adminBMembership, schoolB, "SCHOOL_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCHOOL_ADMIN_SCOPE_DENIED"));

        mvc.perform(post(path, fixture.resultId())
                        .with(principal(adminA, "SCHOOL_ADMIN", adminAMembership, schoolA, "SCHOOL_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicStatus").value("PUBLIC"));
        mvc.perform(post(path, fixture.resultId())
                        .with(principal(adminA, "SCHOOL_ADMIN", adminAMembership, schoolA, "SCHOOL_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    private void submit(UUID resultId) throws Exception {
        mvc.perform(post("/api/v1/activity-results/{id}/submit-public-review", resultId)
                        .with(principal(adminA, "SCHOOL_ADMIN", adminAMembership, schoolA, "SCHOOL_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    private Fixture insertPublishedResult() {
        UUID activityId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO activities(id, school_id, title, execution_status, public_status, created_by)
                VALUES (?, ?, ?, 'PUBLISHED', 'NOT_SUBMITTED', ?)
                """, activityId, schoolA, prefix + "-activity-" + activityId, adminA);
        jdbc.update("""
                INSERT INTO activity_results(
                    id, school_id, activity_id, result_internal_status, result_public_status
                ) VALUES (?, ?, ?, 'INTERNAL_PUBLISHED', 'NOT_SUBMITTED')
                """, resultId, schoolA, activityId);
        jdbc.update("""
                INSERT INTO result_versions(
                    id, result_id, version_number, title, summary_text, score_highlights,
                    media_refs, published_internally_at
                ) VALUES (?, ?, 1, 'Candidate', 'Summary', '[]'::jsonb, '[]'::jsonb, now())
                """, versionId, resultId);
        jdbc.update("""
                UPDATE activity_results
                SET current_candidate_version_id = ?, current_internal_version_id = ?
                WHERE id = ?
                """, versionId, versionId, resultId);
        return new Fixture(resultId, versionId);
    }

    private RequestPostProcessor principal(
            UUID userId,
            String role,
            UUID membershipId,
            UUID schoolId,
            String membershipRole) {
        List<AuthenticatedSchoolMembership> memberships = membershipId == null
                ? List.of()
                : List.of(new AuthenticatedSchoolMembership(membershipId, schoolId, membershipRole));
        var details = new CampusGuinnessUserDetails(
                userId,
                prefix + "-principal",
                "{noop}password",
                "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_" + role)),
                memberships);
        return user(details);
    }

    private UUID insertSchool(String label) {
        UUID id = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO schools(
                    id, name, unified_code_type, unified_code, internal_code, school_type, region,
                    address, contact_name, contact_phone, contact_email, school_status
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, prefix + "-" + label, "USCC", prefix.substring(0, 8) + suffix + "u",
                prefix.substring(0, 8) + suffix + "i", "PRIMARY", "Beijing",
                "Address", "Contact", "13800000000", prefix + "@example.com", "NORMAL");
        return id;
    }

    private UUID insertUser(String label, String platformRole) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users(id, username, password_hash, account_status, platform_role)
                VALUES (?, ?, '{noop}password', 'NORMAL', ?)
                """, id, prefix + "-" + label + "-" + UUID.randomUUID().toString().substring(0, 8), platformRole);
        return id;
    }

    private UUID insertMembership(UUID userId, UUID schoolId, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO school_memberships(id, user_id, school_id, role_in_school, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, id, userId, schoolId, role);
        return id;
    }

    private int reviewCount(UUID resultId, String action) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM result_review_records WHERE result_id = ? AND action = ?
                """, Integer.class, resultId, action);
    }

    private record Fixture(UUID resultId, UUID versionId) {
    }
}
