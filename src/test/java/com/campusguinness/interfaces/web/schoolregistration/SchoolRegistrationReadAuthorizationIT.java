package com.campusguinness.interfaces.web.schoolregistration;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.infrastructure.security.AuthenticatedSchoolMembership;
import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "campus-guinness.security.cors.allowed-origins=http://localhost:5173")
class SchoolRegistrationReadAuthorizationIT extends PostgreSqlIntegrationTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    private final String runPrefix = "stage11-registration-" + UUID.randomUUID();
    private UUID schoolId;
    private UUID superAdminId;
    private UUID schoolAdminId;
    private UUID studentId;

    @BeforeEach
    void setUp() {
        schoolId = insertSchool();
        superAdminId = insertUser("super-admin", "SUPER_ADMIN");
        schoolAdminId = insertUser("school-admin", null);
        studentId = insertUser("student", null);
        insertMembership(schoolAdminId, "SCHOOL_ADMIN");
        insertMembership(studentId, "STUDENT");
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
    void authoritativeSuperAdminReadsPersistedListWithFilterAndPagination() throws Exception {
        UUID newestSubmitted = insertRegistration(
                "newest-submitted", "SUBMITTED", Instant.parse("2999-08-13T02:00:00Z"), true);
        UUID olderSubmitted = insertRegistration(
                "older-submitted", "SUBMITTED", Instant.parse("2998-08-13T02:00:00Z"), false);
        insertRegistration("rejected", "REJECTED", Instant.parse("2997-08-13T02:00:00Z"), false);

        JsonNode firstPage = responseJson(mvc.perform(get("/api/v1/school-registrations")
                        .queryParam("status", "SUBMITTED")
                        .queryParam("page", "0")
                        .queryParam("size", "1")
                        .with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items[0].id").value(newestSubmitted.toString()))
                .andExpect(jsonPath("$.items[0].schoolName").value(runPrefix + "-newest-submitted"))
                .andExpect(jsonPath("$.items[0].schoolType").value("UNIVERSITY"))
                .andExpect(jsonPath("$.items[0].region").value("Zhejiang"))
                .andExpect(jsonPath("$.items[0].contactName").value("Stage 11 Contact"))
                .andExpect(jsonPath("$.items[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$.items[0].createdAt").value("2999-08-13T02:00:00Z"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn().getResponse().getContentAsString());

        assertThat(firstPage.path("totalElements").asLong()).isGreaterThanOrEqualTo(2);

        mvc.perform(get("/api/v1/school-registrations")
                        .queryParam("status", "SUBMITTED")
                        .queryParam("page", "1")
                        .queryParam("size", "1")
                        .with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(olderSubmitted.toString()))
                .andExpect(jsonPath("$.items[0].status").value("SUBMITTED"));
    }

    @Test
    void authoritativeSuperAdminReadsCompleteDetailWithoutEvidenceStorageKey() throws Exception {
        UUID registrationId = insertRegistration(
                "detail", "REJECTED", Instant.parse("2026-08-13T02:00:00Z"), true);

        mvc.perform(get("/api/v1/school-registrations/{id}", registrationId).with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(registrationId.toString()))
                .andExpect(jsonPath("$.schoolName").value(runPrefix + "-detail"))
                .andExpect(jsonPath("$.unifiedCodeType").value("USCC"))
                .andExpect(jsonPath("$.unifiedCode").value("STAGE11-DETAIL"))
                .andExpect(jsonPath("$.schoolType").value("UNIVERSITY"))
                .andExpect(jsonPath("$.region").value("Zhejiang"))
                .andExpect(jsonPath("$.address").value("Stage 11 long address"))
                .andExpect(jsonPath("$.contactName").value("Stage 11 Contact"))
                .andExpect(jsonPath("$.contactPhone").value("13800000000"))
                .andExpect(jsonPath("$.contactEmail").value("stage11@example.com"))
                .andExpect(jsonPath("$.description").value("Stage 11 registration detail"))
                .andExpect(jsonPath("$.evidenceSubmitted").value(true))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewedBy").value(superAdminId.toString()))
                .andExpect(jsonPath("$.reviewComment").value("Reviewed during Stage 11"))
                .andExpect(jsonPath("$.rejectReason").value("Fixture rejection"))
                .andExpect(jsonPath("$.createdAt").value("2026-08-13T02:00:00Z"))
                .andExpect(jsonPath("$.evidenceFileKey").doesNotExist());
    }

    @Test
    void unknownDetailAndInvalidQueryReturnStableClientErrors() throws Exception {
        UUID unknownId = UUID.randomUUID();

        mvc.perform(get("/api/v1/school-registrations/{id}", unknownId).with(superAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHOOL_REGISTRATION_NOT_FOUND"));
        mvc.perform(get("/api/v1/school-registrations?page=-1").with(superAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/v1/school-registrations?size=0").with(superAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/v1/school-registrations?size=101").with(superAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/v1/school-registrations?status=NOT_A_STATUS").with(superAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void schoolAdminStudentAndAnonymousCannotReadListOrDetail() throws Exception {
        UUID registrationId = insertRegistration(
                "role-boundary", "SUBMITTED", Instant.parse("2026-08-13T02:00:00Z"), false);

        assertRoleDenied(schoolAdmin(), registrationId);
        assertRoleDenied(student(), registrationId);

        mvc.perform(get("/api/v1/school-registrations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(get("/api/v1/school-registrations/{id}", registrationId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void forgedSuperAdminAuthorityCannotBypassAuthoritativeDatabaseIdentity() throws Exception {
        UUID registrationId = insertRegistration(
                "forged-role", "SUBMITTED", Instant.parse("2026-08-13T02:00:00Z"), false);
        RequestPostProcessor forged = principal(studentId, "SUPER_ADMIN", memberships(studentId, "STUDENT"));

        mvc.perform(get("/api/v1/school-registrations").with(forged))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLATFORM_GOVERNANCE_DENIED"));
        mvc.perform(get("/api/v1/school-registrations/{id}", registrationId).with(forged))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLATFORM_GOVERNANCE_DENIED"));
    }

    @Test
    void anonymousSchoolRegistrationSubmissionRemainsAccessible() throws Exception {
        String schoolName = runPrefix + "-anonymous-submit";

        mvc.perform(post("/api/v1/school-registrations")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schoolName":"%s",
                                  "unifiedCodeType":"USCC",
                                  "unifiedCode":"STAGE11-ANON",
                                  "schoolType":"UNIVERSITY",
                                  "region":"Zhejiang",
                                  "address":"Anonymous submit address",
                                  "contactName":"Anonymous Contact",
                                  "contactPhone":"13800000001",
                                  "contactEmail":"anonymous-stage11@example.com",
                                  "description":"Anonymous submission regression"
                                }
                                """.formatted(schoolName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schoolName").value(schoolName))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM school_registrations WHERE school_name = ?",
                Integer.class,
                schoolName
        )).isOne();
    }

    private void assertRoleDenied(RequestPostProcessor actor, UUID registrationId) throws Exception {
        mvc.perform(get("/api/v1/school-registrations").with(actor))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/api/v1/school-registrations/{id}", registrationId).with(actor))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private JsonNode responseJson(String response) throws Exception {
        return objectMapper.readTree(response);
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
                id, runPrefix + "-school", "USCC", "stage11-uc-" + suffix,
                "stage11-ic-" + suffix, "UNIVERSITY", "Zhejiang", "Address", "Contact",
                "13800000000", "stage11-school@example.com", "NORMAL");
        return id;
    }

    private UUID insertUser(String label, String platformRole) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, username, password_hash, account_status, platform_role) VALUES (?,?,?,?,?)",
                id, runPrefix + "-" + label, "{noop}password", "NORMAL", platformRole);
        return id;
    }

    private void insertMembership(UUID userId, String role) {
        jdbc.update("""
                INSERT INTO school_memberships(id, user_id, school_id, role_in_school, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, UUID.randomUUID(), userId, schoolId, role);
    }

    private UUID insertRegistration(String label, String status, Instant createdAt, boolean evidence) {
        UUID id = UUID.randomUUID();
        boolean reviewed = "REJECTED".equals(status);
        OffsetDateTime createdDateTime = createdAt.atOffset(ZoneOffset.UTC);
        OffsetDateTime reviewedDateTime = reviewed ? createdAt.plusSeconds(3600).atOffset(ZoneOffset.UTC) : null;
        jdbc.update("""
                INSERT INTO school_registrations(
                    id, school_name, unified_code_type, unified_code, school_type, region, address,
                    contact_name, contact_phone, contact_email, description, evidence_file_key,
                    registration_status, reviewed_by, reviewed_at, review_comment, reject_reason,
                    created_at, updated_at, version
                ) VALUES (?, ?, 'USCC', ?, 'UNIVERSITY', 'Zhejiang', 'Stage 11 long address',
                          'Stage 11 Contact', '13800000000', 'stage11@example.com',
                          'Stage 11 registration detail', ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """,
                id,
                runPrefix + "-" + label,
                "STAGE11-" + label.toUpperCase(),
                evidence ? "private/stage11/evidence.pdf" : null,
                status,
                reviewed ? superAdminId : null,
                reviewedDateTime,
                reviewed ? "Reviewed during Stage 11" : null,
                reviewed ? "Fixture rejection" : null,
                createdDateTime,
                createdAt.plusSeconds(3600).atOffset(ZoneOffset.UTC));
        return id;
    }
}
