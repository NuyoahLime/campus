package com.campusguinness.interfaces.web.schoolregistration;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.infrastructure.security.AuthenticatedSchoolMembership;
import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import com.campusguinness.school.application.port.SchoolRegistrationRepository;
import com.campusguinness.school.internal.domain.SchoolRegistrationId;
import com.campusguinness.school.internal.persistence.SchoolRegistrationConcurrentReviewException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SchoolRegistrationReviewAuthorizationIT extends PostgreSqlIntegrationTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SchoolRegistrationRepository registrations;

    private final String prefix = "stage12-review-" + UUID.randomUUID();
    private UUID governanceSchoolId;
    private UUID superAdminId;
    private UUID schoolAdminId;
    private UUID studentId;

    @BeforeEach
    void setUp() {
        governanceSchoolId = insertSchool("governance", "GOVERNANCE-" + shortId(), "NORMAL");
        superAdminId = insertUser("super-admin", "SUPER_ADMIN");
        schoolAdminId = insertUser("school-admin", null);
        studentId = insertUser("student", null);
        insertMembership(schoolAdminId, "SCHOOL_ADMIN");
        insertMembership(studentId, "STUDENT");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM school_registrations WHERE school_name LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM school_memberships WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)",
                prefix + "%");
        jdbc.update("DELETE FROM users WHERE username LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM schools WHERE name LIKE ?", prefix + "%");
    }

    @Test
    void requestSupplementPersistsAuditWithoutChangingCreatedAt() throws Exception {
        Instant createdAt = Instant.parse("2026-01-02T03:04:05Z");
        UUID id = insertRegistration("supplement", "SUPPLEMENT-" + shortId(), "SUBMITTED", createdAt);

        mvc.perform(post("/api/v1/school-registrations/{id}/request-supplement", id)
                        .with(superAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"  Please add the school license  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEED_SUPPLEMENT"));

        var row = jdbc.queryForMap("""
                SELECT registration_status, reviewed_by, reviewed_at, review_comment,
                       created_at, updated_at
                FROM school_registrations WHERE id = ?
                """, id);
        assertThat(row.get("registration_status")).isEqualTo("NEED_SUPPLEMENT");
        assertThat(row.get("reviewed_by")).isEqualTo(superAdminId);
        assertThat(row.get("reviewed_at")).isNotNull();
        assertThat(row.get("review_comment")).isEqualTo("Please add the school license");
        assertThat(((Timestamp) row.get("created_at")).toInstant()).isEqualTo(createdAt);
        assertThat(((Timestamp) row.get("updated_at")).toInstant()).isAfter(createdAt);
    }

    @Test
    void rejectPersistsReasonAndAudit() throws Exception {
        UUID id = insertRegistration("reject", "REJECT-" + shortId(), "SUBMITTED", Instant.now().minusSeconds(3600));

        mvc.perform(post("/api/v1/school-registrations/{id}/reject", id)
                        .with(superAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  Registration data mismatch  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        var row = jdbc.queryForMap("""
                SELECT registration_status, reviewed_by, reviewed_at, reject_reason
                FROM school_registrations WHERE id = ?
                """, id);
        assertThat(row.get("registration_status")).isEqualTo("REJECTED");
        assertThat(row.get("reviewed_by")).isEqualTo(superAdminId);
        assertThat(row.get("reviewed_at")).isNotNull();
        assertThat(row.get("reject_reason")).isEqualTo("Registration data mismatch");
    }

    @Test
    void approveCreatesPendingSchoolAndLinksRegistration() throws Exception {
        String unifiedCode = "APPROVE-" + shortId();
        UUID id = insertRegistration("approve", unifiedCode, "SUBMITTED", Instant.now().minusSeconds(3600));

        mvc.perform(post("/api/v1/school-registrations/{id}/approve", id)
                        .with(superAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"  Verified  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.createdSchoolId").isNotEmpty());

        UUID schoolId = jdbc.queryForObject(
                "SELECT created_school_id FROM school_registrations WHERE id = ?", UUID.class, id);
        var school = jdbc.queryForMap("""
                SELECT name, unified_code_type, unified_code, internal_code, school_status,
                       school_type, region, address, contact_name, contact_phone, contact_email
                FROM schools WHERE id = ?
                """, schoolId);
        assertThat(school.get("name")).isEqualTo(prefix + "-approve");
        assertThat(school.get("unified_code_type")).isEqualTo("USCC");
        assertThat(school.get("unified_code")).isEqualTo(unifiedCode);
        assertThat(school.get("internal_code").toString()).hasSize(32).doesNotContain("-");
        assertThat(school.get("school_status")).isEqualTo("PENDING_ENABLE");
        assertThat(school.get("school_type")).isEqualTo("UNIVERSITY");
        assertThat(school.get("region")).isEqualTo("Zhejiang");
        assertThat(school.get("address")).isEqualTo("Stage 12 address");
        assertThat(school.get("contact_name")).isEqualTo("Stage 12 Contact");
        assertThat(school.get("contact_phone")).isEqualTo("13800000012");
        assertThat(school.get("contact_email")).isEqualTo("stage12@example.com");
        assertThat(jdbc.queryForObject(
                "SELECT review_comment FROM school_registrations WHERE id = ?", String.class, id))
                .isEqualTo("Verified");
    }

    @Test
    void approveWithoutUnifiedCodeUsesNullableMigration() throws Exception {
        UUID id = insertRegistration("no-code", null, "SUBMITTED", Instant.now().minusSeconds(3600));

        mvc.perform(post("/api/v1/school-registrations/{id}/approve", id)
                        .with(superAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        UUID schoolId = jdbc.queryForObject(
                "SELECT created_school_id FROM school_registrations WHERE id = ?", UUID.class, id);
        var school = jdbc.queryForMap(
                "SELECT unified_code, internal_code, school_status FROM schools WHERE id = ?", schoolId);
        assertThat(school.get("unified_code")).isNull();
        assertThat(school.get("internal_code").toString()).hasSize(32);
        assertThat(school.get("school_status")).isEqualTo("PENDING_ENABLE");
    }

    @Test
    void duplicateUnifiedCodeReturns409AndRollsBackRegistration() throws Exception {
        String duplicateCode = "DUPLICATE-" + shortId();
        insertSchool("existing", duplicateCode, "NORMAL");
        UUID id = insertRegistration("duplicate", duplicateCode, "SUBMITTED", Instant.now().minusSeconds(3600));
        int schoolsBefore = countSchools();

        mvc.perform(post("/api/v1/school-registrations/{id}/approve", id)
                        .with(superAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHOOL_UNIFIED_CODE_CONFLICT"));

        assertThat(countSchools()).isEqualTo(schoolsBefore);
        assertThat(registrationStatus(id)).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject(
                "SELECT created_school_id IS NULL FROM school_registrations WHERE id = ?", Boolean.class, id))
                .isTrue();
    }

    @Test
    void invalidTransitionsReturn409WithoutCreatingOrphanSchools() throws Exception {
        UUID needSupplement = insertRegistration("need-supplement", "NS-" + shortId(),
                "NEED_SUPPLEMENT", Instant.now());
        UUID approved = insertRegistration("already-approved", "AA-" + shortId(),
                "APPROVED", Instant.now());
        UUID rejected = insertRegistration("already-rejected", "AR-" + shortId(),
                "REJECTED", Instant.now());
        int schoolsBefore = countSchools();

        assertConflict(needSupplement, "approve", "{\"comment\":\"ok\"}");
        assertConflict(needSupplement, "reject", "{\"reason\":\"no\"}");
        assertConflict(approved, "approve", "{}");
        assertConflict(approved, "reject", "{\"reason\":\"no\"}");
        assertConflict(rejected, "approve", "{}");

        assertThat(countSchools()).isEqualTo(schoolsBefore);
    }

    @Test
    void allReviewActionsEnforceRoleAndAuthoritativeIdentity() throws Exception {
        UUID id = insertRegistration("authorization", "AUTH-" + shortId(), "SUBMITTED", Instant.now());
        for (String action : List.of("request-supplement", "approve", "reject")) {
            String body = action.equals("reject") ? "{\"reason\":\"reason\"}"
                    : "{\"comment\":\"comment\"}";
            mvc.perform(post("/api/v1/school-registrations/{id}/{action}", id, action)
                            .with(schoolAdmin()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/school-registrations/{id}/{action}", id, action)
                            .with(student()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/school-registrations/{id}/{action}", id, action)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/school-registrations/{id}/{action}", id, action)
                            .with(forgedSuperAdmin()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PLATFORM_GOVERNANCE_DENIED"));
        }
        assertThat(registrationStatus(id)).isEqualTo("SUBMITTED");
    }

    @Test
    void staleAggregateCannotOverwriteCompletedReview() {
        UUID id = insertRegistration("stale", "STALE-" + shortId(), "SUBMITTED", Instant.now());
        var first = registrations.findById(new SchoolRegistrationId(id)).orElseThrow();
        var stale = registrations.findById(new SchoolRegistrationId(id)).orElseThrow();

        first.requestSupplement(superAdminId, "first review");
        registrations.save(first);
        stale.reject(superAdminId, "stale review");

        assertThatThrownBy(() -> registrations.save(stale))
                .isInstanceOf(SchoolRegistrationConcurrentReviewException.class);
        assertThat(registrationStatus(id)).isEqualTo("NEED_SUPPLEMENT");
        assertThat(jdbc.queryForObject(
                "SELECT review_comment FROM school_registrations WHERE id = ?", String.class, id))
                .isEqualTo("first review");
    }

    private void assertConflict(UUID id, String action, String body) throws Exception {
        mvc.perform(post("/api/v1/school-registrations/{id}/{action}", id, action)
                        .with(superAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    private RequestPostProcessor superAdmin() {
        return principal(superAdminId, "SUPER_ADMIN", List.of());
    }

    private RequestPostProcessor schoolAdmin() {
        return principal(schoolAdminId, "SCHOOL_ADMIN", memberships(schoolAdminId, "SCHOOL_ADMIN"));
    }

    private RequestPostProcessor student() {
        return principal(studentId, "STUDENT", memberships(studentId, "STUDENT"));
    }

    private RequestPostProcessor forgedSuperAdmin() {
        return principal(studentId, "SUPER_ADMIN", memberships(studentId, "STUDENT"));
    }

    private RequestPostProcessor principal(
            UUID userId,
            String role,
            List<AuthenticatedSchoolMembership> memberships
    ) {
        var details = new CampusGuinnessUserDetails(
                userId, prefix + "-principal", "{noop}password", "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_" + role)), memberships
        );
        return user(details);
    }

    private List<AuthenticatedSchoolMembership> memberships(UUID userId, String role) {
        UUID membershipId = jdbc.queryForObject(
                "SELECT id FROM school_memberships WHERE user_id = ? AND status = 'ACTIVE'",
                UUID.class, userId);
        return List.of(new AuthenticatedSchoolMembership(membershipId, governanceSchoolId, role));
    }

    private UUID insertSchool(String label, String unifiedCode, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO schools(
                    id, name, unified_code_type, unified_code, internal_code, school_type, region,
                    address, contact_name, contact_phone, contact_email, school_status
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id, prefix + "-" + label, "USCC", unifiedCode,
                id.toString().replace("-", ""), "UNIVERSITY", "Zhejiang", "Address",
                "Contact", "13800000000", "stage12-school@example.com", status);
        return id;
    }

    private UUID insertUser(String label, String platformRole) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, username, password_hash, account_status, platform_role) VALUES (?,?,?,?,?)",
                id, prefix + "-" + label, "{noop}password", "NORMAL", platformRole);
        return id;
    }

    private void insertMembership(UUID userId, String role) {
        jdbc.update("""
                INSERT INTO school_memberships(id, user_id, school_id, role_in_school, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, UUID.randomUUID(), userId, governanceSchoolId, role);
    }

    private UUID insertRegistration(String label, String unifiedCode, String status, Instant createdAt) {
        UUID id = UUID.randomUUID();
        OffsetDateTime timestamp = createdAt.atOffset(ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO school_registrations(
                    id, school_name, unified_code_type, unified_code, school_type, region, address,
                    contact_name, contact_phone, contact_email, description, registration_status,
                    created_at, updated_at, version
                ) VALUES (?, ?, 'USCC', ?, 'UNIVERSITY', 'Zhejiang', 'Stage 12 address',
                          'Stage 12 Contact', '13800000012', 'stage12@example.com',
                          'Stage 12 review fixture', ?, ?, ?, 0)
                """, id, prefix + "-" + label, unifiedCode, status, timestamp, timestamp);
        return id;
    }

    private int countSchools() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM schools WHERE name LIKE ?", Integer.class, prefix + "%");
    }

    private String registrationStatus(UUID id) {
        return jdbc.queryForObject(
                "SELECT registration_status FROM school_registrations WHERE id = ?", String.class, id);
    }

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
