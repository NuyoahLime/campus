package com.campusguinness.interfaces.web.l3authorization;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.infrastructure.security.AuthenticatedSchoolMembership;
import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import com.campusguinness.ranking.application.query.port.L3UsableAuthorizationQueryPort;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class L3AuthorizationWorkflowIT extends PostgreSqlIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired L3UsableAuthorizationQueryPort usableAuthorizations;

    private final String prefix = "l3-auth-" + UUID.randomUUID().toString().substring(0, 8);
    private UUID schoolA;
    private UUID schoolB;
    private UUID adminA;
    private UUID adminA2;
    private UUID adminB;
    private UUID superAdmin;
    private UUID projectA;
    private UUID ruleA;
    private UUID projectB;
    private UUID ruleB;
    private UUID activityA1;
    private UUID activityA2;
    private UUID activityB;

    @BeforeEach
    void setUp() {
        schoolA = UUID.randomUUID();
        schoolB = UUID.randomUUID();
        adminA = UUID.randomUUID();
        adminA2 = UUID.randomUUID();
        adminB = UUID.randomUUID();
        superAdmin = UUID.randomUUID();
        projectA = UUID.randomUUID();
        ruleA = UUID.randomUUID();
        projectB = UUID.randomUUID();
        ruleB = UUID.randomUUID();

        insertSchool(schoolA, "school-a", "NORMAL");
        insertSchool(schoolB, "school-b", "NORMAL");
        insertUser(adminA, "admin-a", null);
        insertUser(adminA2, "admin-a2", null);
        insertUser(adminB, "admin-b", null);
        insertUser(superAdmin, "super", "SUPER_ADMIN");
        insertMembership(adminA, schoolA, "SCHOOL_ADMIN");
        insertMembership(adminA2, schoolA, "SCHOOL_ADMIN");
        insertMembership(adminB, schoolB, "SCHOOL_ADMIN");
        insertProject(projectA, ruleA, "project-a");
        insertProject(projectB, ruleB, "project-b");
        activityA1 = insertActivity(schoolA, projectA, ruleA, "activity-a1");
        activityA2 = insertActivity(schoolA, projectA, ruleA, "activity-a2");
        activityB = insertActivity(schoolB, projectA, ruleA, "activity-b");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM l3_authorizations WHERE school_id IN (?, ?)", schoolA, schoolB);
        jdbc.update("""
                DELETE FROM audit_records
                WHERE actor_id IN (SELECT id FROM users WHERE username LIKE ?)
                   OR school_id IN (?, ?)
                   OR target_id IN (?, ?)
                """, prefix + "%", schoolA, schoolB, schoolA, schoolB);
        jdbc.update("DELETE FROM activity_projects WHERE activity_id IN (SELECT id FROM activities WHERE title LIKE ?)", prefix + "%");
        jdbc.update("DELETE FROM activities WHERE title LIKE ?", prefix + "%");
        jdbc.update("UPDATE challenge_projects SET current_rule_version_id = null WHERE name LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM project_rule_versions WHERE project_id IN (SELECT id FROM challenge_projects WHERE name LIKE ?)", prefix + "%");
        jdbc.update("DELETE FROM challenge_projects WHERE name LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM school_memberships WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", prefix + "%");
        jdbc.update("DELETE FROM users WHERE username LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM schools WHERE name LIKE ?", prefix + "%");
    }

    @Test
    void schoolAdminCreateEditSubmitRejectReturnResubmitAndApproveUsesServerDerivedSchoolScope() throws Exception {
        UUID authorizationId = createAsAdminA("""
                {
                  "schoolId":"%s",
                  "projectId":"%s",
                  "ruleVersionId":"%s",
                  "allowSchoolName":true,
                  "allowStudentName":true,
                  "dataScope":{
                    "activityIds":["%s","%s","%s"],
                    "grades":[" G6 ","G5","G5"," "],
                    "classNames":[" C2 ","C1","C1"],
                    "activityPeriodStart":"2026-01-01T00:00:00Z",
                    "activityPeriodEnd":"2026-01-31T00:00:00Z"
                  }
                }
                """.formatted(schoolB, projectA, ruleA, activityA2, activityA1, activityA2));

        assertThat(dbValue(authorizationId, "school_id", UUID.class)).isEqualTo(schoolA);
        JsonNode normalized = objectMapper.readTree(dbValue(authorizationId, "data_scope", String.class));
        assertThat(normalized.get("activityIds")).hasSize(2);
        assertThat(normalized.get("grades")).extracting(JsonNode::asText).containsExactly("G5", "G6");
        assertThat(normalized.get("classNames")).extracting(JsonNode::asText).containsExactly("C1", "C2");

        mvc.perform(get("/api/v1/school-admin/l3-authorizations/{id}", authorizationId)
                        .with(schoolAdmin(adminB, schoolB)))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/school-admin/l3-authorizations/{id}/submit", authorizationId)
                        .with(schoolAdmin(adminA, schoolA))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        mvc.perform(get("/api/v1/super-admin/l3-authorizations")
                        .param("status", "PENDING_REVIEW")
                        .with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(post("/api/v1/super-admin/l3-authorizations/{id}/reject", authorizationId)
                        .with(superAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"scope needs class adjustment\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mvc.perform(get("/api/v1/school-admin/l3-authorizations/{id}", authorizationId)
                        .with(schoolAdmin(adminA, schoolA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rejectReason").value("scope needs class adjustment"));

        mvc.perform(post("/api/v1/school-admin/l3-authorizations/{id}/return-to-draft", authorizationId)
                        .with(schoolAdmin(adminA, schoolA))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mvc.perform(put("/api/v1/school-admin/l3-authorizations/{id}", authorizationId)
                        .with(schoolAdmin(adminA, schoolA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "allowSchoolName":false,
                                  "allowStudentName":false,
                                  "dataScope":{"grades":["G5"],"classNames":["C1"]}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mvc.perform(post("/api/v1/school-admin/l3-authorizations/{id}/submit", authorizationId)
                        .with(schoolAdmin(adminA, schoolA))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        mvc.perform(post("/api/v1/super-admin/l3-authorizations/{id}/approve", authorizationId)
                        .with(superAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"approved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(post("/api/v1/super-admin/l3-authorizations/{id}/approve", authorizationId)
                        .with(schoolAdmin(adminA, schoolA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"no\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/super-admin/l3-authorizations/{id}/reject", authorizationId)
                        .with(schoolAdmin(adminA, schoolA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"no\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/super-admin/l3-authorizations/{id}/resume", authorizationId)
                        .with(schoolAdmin(adminA, schoolA))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/school-admin/l3-authorizations")
                        .with(superAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(projectA, ruleA, "{}")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsRuleVersionMismatchReversePeriodAndCrossSchoolActivities() throws Exception {
        mvc.perform(post("/api/v1/school-admin/l3-authorizations")
                        .with(schoolAdmin(adminA, schoolA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(projectA, ruleB, "{}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Cannot save L3 authorization: ChallengeProject and RuleVersion must exist and match."));

        mvc.perform(post("/api/v1/school-admin/l3-authorizations")
                        .with(schoolAdmin(adminA, schoolA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(projectA, ruleA,
                                "{\"activityPeriodStart\":\"2026-02-01T00:00:00Z\",\"activityPeriodEnd\":\"2026-01-01T00:00:00Z\"}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Cannot save L3 authorization: activityPeriodStart must not be after activityPeriodEnd."));

        mvc.perform(post("/api/v1/school-admin/l3-authorizations")
                        .with(schoolAdmin(adminA, schoolA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(projectA, ruleA,
                                "{\"activityIds\":[\"" + activityB + "\"]}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Cannot save L3 authorization: dataScope.activityIds must belong to the school, ChallengeProject, and RuleVersion."));
    }

    @Test
    void duplicatePolicyPreservesWithdrawnHistoryAndBlocksActiveDuplicates() throws Exception {
        UUID first = createAsAdminA(payload(projectA, ruleA, "{\"grades\":[\"G5\"]}"));

        mvc.perform(post("/api/v1/school-admin/l3-authorizations")
                        .with(schoolAdmin(adminA, schoolA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(projectA, ruleA, "{\"grades\":[\"G5\"]}")))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/school-admin/l3-authorizations/{id}/withdraw", first)
                        .with(schoolAdmin(adminA, schoolA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"replace with new authorization\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));

        UUID second = createAsAdminA(payload(projectA, ruleA, "{\"grades\":[\"G5\"]}"));
        assertThat(second).isNotEqualTo(first);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM l3_authorizations
                WHERE school_id = ? AND project_id = ? AND rule_version_id = ?
                """, Integer.class, schoolA, projectA, ruleA)).isEqualTo(2);
    }

    @Test
    void schoolLifecycleSuspendsRequiresManualResumeAndWithdrawsAuthorizationsFailClosed() throws Exception {
        UUID approved = createSubmittedApproved("{\"grades\":[\"G5\"]}");
        UUID draft = createAsAdminA(payload(projectA, ruleA, "{\"grades\":[\"G6\"]}"));

        assertThat(usableAuthorizations.findUsableAuthorizations(projectA, ruleA)).hasSize(1);

        mvc.perform(post("/api/v1/schools/{id}/suspend", schoolA)
                        .with(superAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"platform pause\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        assertThat(statusOf(approved)).isEqualTo("SUSPENDED");
        assertThat(statusOf(draft)).isEqualTo("DRAFT");
        assertThat(usableAuthorizations.findUsableAuthorizations(projectA, ruleA)).isEmpty();

        mvc.perform(post("/api/v1/schools/{id}/restore", schoolA)
                        .with(superAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"issue resolved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NORMAL"));
        assertThat(statusOf(approved)).isEqualTo("SUSPENDED");
        assertThat(usableAuthorizations.findUsableAuthorizations(projectA, ruleA)).isEmpty();

        mvc.perform(post("/api/v1/super-admin/l3-authorizations/{id}/resume", approved)
                        .with(superAdmin())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(usableAuthorizations.findUsableAuthorizations(projectA, ruleA)).hasSize(1);

        mvc.perform(post("/api/v1/schools/{id}/disable", schoolA)
                        .with(superAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"operations ended\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));
        assertThat(statusOf(approved)).isEqualTo("WITHDRAWN");
        assertThat(statusOf(draft)).isEqualTo("WITHDRAWN");
        assertThat(usableAuthorizations.findUsableAuthorizations(projectA, ruleA)).isEmpty();

        mvc.perform(post("/api/v1/school-admin/l3-authorizations/{id}/submit", approved)
                        .with(schoolAdmin(adminA, schoolA))
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    private UUID createSubmittedApproved(String dataScopeJson) throws Exception {
        UUID id = createAsAdminA(payload(projectA, ruleA, dataScopeJson));
        mvc.perform(post("/api/v1/school-admin/l3-authorizations/{id}/submit", id)
                        .with(schoolAdmin(adminA, schoolA))
                        .with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/super-admin/l3-authorizations/{id}/approve", id)
                        .with(superAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"approved\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private UUID createAsAdminA(String payload) throws Exception {
        String content = mvc.perform(post("/api/v1/school-admin/l3-authorizations")
                        .with(schoolAdmin(adminA, schoolA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(content).get("id").asText());
    }

    private String payload(UUID projectId, UUID ruleVersionId, String dataScopeJson) {
        return """
                {
                  "projectId":"%s",
                  "ruleVersionId":"%s",
                  "allowSchoolName":true,
                  "allowStudentName":false,
                  "dataScope":%s
                }
                """.formatted(projectId, ruleVersionId, dataScopeJson);
    }

    private String statusOf(UUID authorizationId) {
        return dbValue(authorizationId, "authorization_status", String.class);
    }

    private <T> T dbValue(UUID authorizationId, String column, Class<T> type) {
        return jdbc.queryForObject("SELECT " + column + " FROM l3_authorizations WHERE id = ?",
                type, authorizationId);
    }

    private void insertSchool(UUID id, String label, String status) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO schools(
                    id, name, unified_code_type, unified_code, internal_code, school_type, region,
                    address, contact_name, contact_phone, contact_email, school_status
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id, prefix + "-" + label, "USCC", prefix + "-" + suffix + "-uc",
                prefix + "-" + suffix + "-ic", "PRIMARY", "Beijing", "Address", "Contact",
                "13800000000", prefix + "@example.com", status);
    }

    private void insertUser(UUID id, String label, String platformRole) {
        jdbc.update("INSERT INTO users(id, username, password_hash, account_status, platform_role) VALUES (?,?,?,?,?)",
                id, prefix + "-" + label, "{noop}password", "NORMAL", platformRole);
    }

    private void insertMembership(UUID userId, UUID schoolId, String role) {
        jdbc.update("""
                INSERT INTO school_memberships(id, user_id, school_id, role_in_school, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, UUID.randomUUID(), userId, schoolId, role);
    }

    private void insertProject(UUID projectId, UUID ruleVersionId, String label) {
        jdbc.update("""
                INSERT INTO challenge_projects(
                    id, name, category, score_storage_type, score_indicator_type,
                    comparison_direction, score_unit, effective_score_rule, project_status
                ) VALUES (?,?,?,?,?,?,?,?,?)
                """,
                projectId, prefix + "-" + label, "SPORTS", "INTEGER", "NUMERIC",
                "HIGHER_BETTER", "points", "BEST", "PUBLISHED");
        jdbc.update("""
                INSERT INTO project_rule_versions(
                    id, project_id, version_number, score_storage_type, score_indicator_type,
                    comparison_direction, score_unit, effective_score_rule, rules_text, created_by
                ) VALUES (?,?,?,?,?,?,?,?,?,?)
                """,
                ruleVersionId, projectId, 1, "INTEGER", "NUMERIC", "HIGHER_BETTER", "points",
                "BEST", prefix + "-rules", superAdmin);
        jdbc.update("UPDATE challenge_projects SET current_rule_version_id = ? WHERE id = ?", ruleVersionId, projectId);
    }

    private UUID insertActivity(UUID schoolId, UUID projectId, UUID ruleVersionId, String label) {
        UUID activityId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO activities(
                    id, school_id, title, start_time, end_time, execution_status, public_status, created_by
                ) VALUES (?,?,?,?,?,?,?,?)
                """, activityId, schoolId, prefix + "-" + label,
                Timestamp.from(Instant.parse("2026-01-10T00:00:00Z")),
                Timestamp.from(Instant.parse("2026-01-10T02:00:00Z")),
                "PUBLISHED", "PUBLIC", adminA);
        jdbc.update("INSERT INTO activity_projects(id, activity_id, project_id, rule_version_id) VALUES (?,?,?,?)",
                UUID.randomUUID(), activityId, projectId, ruleVersionId);
        return activityId;
    }

    private RequestPostProcessor schoolAdmin(UUID userId, UUID schoolId) {
        var details = new CampusGuinnessUserDetails(
                userId,
                prefix + "-school-admin",
                "{noop}password",
                "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_SCHOOL_ADMIN")),
                List.of(new AuthenticatedSchoolMembership(UUID.randomUUID(), schoolId, "SCHOOL_ADMIN")));
        return user(details);
    }

    private RequestPostProcessor superAdmin() {
        var details = new CampusGuinnessUserDetails(
                superAdmin,
                prefix + "-super",
                "{noop}password",
                "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")),
                List.of());
        return user(details);
    }
}
