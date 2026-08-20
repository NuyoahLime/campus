package com.campusguinness.interfaces.web.security;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.infrastructure.security.AuthenticatedSchoolMembership;
import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class Stage21AuthorizationIT extends PostgreSqlIntegrationTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private UUID schoolA;
    private UUID schoolB;
    private UUID studentA;
    private UUID studentB;
    private UUID studentC;
    private UUID schoolAdminA;
    private UUID schoolAdminB;
    private UUID superAdmin;
    private UUID studentAMembership;
    private UUID studentBMembership;
    private UUID studentCMembership;
    private UUID schoolAdminAMembership;
    private UUID schoolAdminBMembership;
    private UUID scoreA;
    private UUID appealA;
    private UUID appealB;
    private UUID appealC;
    private UUID feedbackA;
    private UUID feedbackB;
    private UUID feedbackC;

    @BeforeEach
    void setUp() {
        schoolA = insertSchool("A");
        schoolB = insertSchool("B");
        studentA = insertUser("student-a", null);
        studentB = insertUser("student-b", null);
        studentC = insertUser("student-c", null);
        schoolAdminA = insertUser("school-admin-a", null);
        schoolAdminB = insertUser("school-admin-b", null);
        superAdmin = insertUser("super-admin", "SUPER_ADMIN");

        studentAMembership = insertMembership(studentA, schoolA, "STUDENT");
        studentBMembership = insertMembership(studentB, schoolA, "STUDENT");
        studentCMembership = insertMembership(studentC, schoolB, "STUDENT");
        schoolAdminAMembership = insertMembership(schoolAdminA, schoolA, "SCHOOL_ADMIN");
        schoolAdminBMembership = insertMembership(schoolAdminB, schoolB, "SCHOOL_ADMIN");

        scoreA = insertScoreGraph(schoolA, studentA, schoolAdminA, "A");
        UUID scoreB = insertScoreGraph(schoolA, studentB, schoolAdminA, "B");
        UUID scoreC = insertScoreGraph(schoolB, studentC, schoolAdminB, "C");
        appealA = insertAppeal(schoolA, studentA, scoreA, "SCORE", "SUBMITTED");
        appealB = insertAppeal(schoolA, studentB, scoreB, "SCORE", "SUBMITTED");
        appealC = insertAppeal(schoolB, studentC, scoreC, "SCORE", "SUBMITTED");
        feedbackA = insertFeedback(schoolA, studentA, "SUBMITTED");
        feedbackB = insertFeedback(schoolA, studentB, "SUBMITTED");
        feedbackC = insertFeedback(schoolB, studentC, "SUBMITTED");
    }

    @Test
    void stage21RoutesEnforceRoleMatrixThroughSecurityFilterChain() throws Exception {
        String missing = UUID.randomUUID().toString();
        List<Route> studentRoutes = List.of(
                getRoute("/api/v1/student/appeals"),
                postRoute("/api/v1/student/appeals", appealSubmit(UUID.randomUUID(), "SCORE")),
                getRoute("/api/v1/student/appeals/" + missing),
                postRoute("/api/v1/student/appeals/" + missing + "/withdraw", null),
                getRoute("/api/v1/student/feedback"),
                postRoute("/api/v1/student/feedback", "{\"feedbackType\":\"GENERAL\",\"content\":\"stage21 auth\"}"),
                getRoute("/api/v1/student/feedback/" + missing),
                postRoute("/api/v1/student/feedback/" + missing + "/close", "{\"reason\":\"done\"}"));

        List<Route> schoolAdminRoutes = List.of(
                getRoute("/api/v1/school-admin/appeals"),
                getRoute("/api/v1/school-admin/appeals/" + missing),
                postRoute("/api/v1/school-admin/appeals/" + missing + "/begin-processing", null),
                postRoute("/api/v1/school-admin/appeals/" + missing + "/reject", "{\"resolution\":\"rejected\"}"),
                getRoute("/api/v1/school-admin/feedback"),
                getRoute("/api/v1/school-admin/feedback/" + missing),
                postRoute("/api/v1/school-admin/feedback/" + missing + "/begin-processing", null),
                postRoute("/api/v1/school-admin/feedback/" + missing + "/resolve", "{\"reply\":\"resolved\"}"));

        for (Route route : studentRoutes) {
            assertRoleMatrix(route, Role.STUDENT);
        }
        for (Route route : schoolAdminRoutes) {
            assertRoleMatrix(route, Role.SCHOOL_ADMIN);
        }
    }

    @Test
    void studentHttpScopeRestrictsListsDetailsAndMutations() throws Exception {
        RequestPostProcessor student = principal(Role.STUDENT, studentA, schoolA, studentAMembership);

        mvc.perform(get("/api/v1/student/appeals").with(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].appealId").value(appealA.toString()));
        mvc.perform(get("/api/v1/student/appeals/{id}", appealA).with(student)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/student/appeals/{id}", appealB).with(student)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/student/appeals/{id}", appealC).with(student)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/student/appeals/{id}/withdraw", appealA).with(student).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/student/appeals/{id}/withdraw", appealB).with(student).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/student/appeals/{id}/withdraw", appealC).with(student).with(csrf()))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/student/feedback").with(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].feedbackId").value(feedbackA.toString()));
        mvc.perform(get("/api/v1/student/feedback/{id}", feedbackA).with(student)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/student/feedback/{id}", feedbackB).with(student)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/student/feedback/{id}", feedbackC).with(student)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/student/feedback/{id}/close", feedbackA).with(student).with(csrf())
                        .contentType("application/json").content("{\"reason\":\"done\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/student/feedback/{id}/close", feedbackB).with(student).with(csrf())
                        .contentType("application/json").content("{\"reason\":\"done\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/student/feedback/{id}/close", feedbackC).with(student).with(csrf())
                        .contentType("application/json").content("{\"reason\":\"done\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void schoolAdminHttpScopeRestrictsListsDetailsAndMutations() throws Exception {
        RequestPostProcessor admin = principal(Role.SCHOOL_ADMIN, schoolAdminA, schoolA, schoolAdminAMembership);

        mvc.perform(get("/api/v1/school-admin/appeals").with(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/school-admin/appeals/{id}", appealA).with(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/school-admin/appeals/{id}", appealC).with(admin)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/school-admin/appeals/{id}/begin-processing", appealB).with(admin).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/school-admin/appeals/{id}/reject", appealB).with(admin).with(csrf())
                        .contentType("application/json").content("{\"resolution\":\"rejected\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/school-admin/appeals/{id}/begin-processing", appealC).with(admin).with(csrf()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/school-admin/feedback").with(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/school-admin/feedback/{id}", feedbackA).with(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/school-admin/feedback/{id}", feedbackC).with(admin)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/school-admin/feedback/{id}/begin-processing", feedbackB).with(admin).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/school-admin/feedback/{id}/resolve", feedbackB).with(admin).with(csrf())
                        .contentType("application/json").content("{\"reply\":\"resolved\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/school-admin/feedback/{id}/begin-processing", feedbackC).with(admin).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void staleStudentSessionCannotWithdrawAppealOrCloseFeedback() throws Exception {
        RequestPostProcessor stale = principal(Role.STUDENT, studentA, schoolA, studentAMembership);
        jdbc.update("UPDATE school_memberships SET status = 'ENDED', ended_at = now() WHERE id = ?",
                studentAMembership);

        mvc.perform(post("/api/v1/student/appeals/{id}/withdraw", appealA).with(stale).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_SCOPE_DENIED"));
        mvc.perform(post("/api/v1/student/feedback/{id}/close", feedbackA).with(stale).with(csrf())
                        .contentType("application/json").content("{\"reason\":\"done\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_SCOPE_DENIED"));
    }

    @Test
    void studentSessionWithoutMembershipCannotWithdrawAppealOrCloseFeedback() throws Exception {
        RequestPostProcessor missing = principal(Role.STUDENT, studentA, schoolA, studentAMembership);
        jdbc.update("DELETE FROM school_memberships WHERE id = ?", studentAMembership);

        mvc.perform(post("/api/v1/student/appeals/{id}/withdraw", appealA).with(missing).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_SCOPE_DENIED"));
        mvc.perform(post("/api/v1/student/feedback/{id}/close", feedbackA).with(missing).with(csrf())
                        .contentType("application/json").content("{\"reason\":\"done\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_SCOPE_DENIED"));
    }

    @Test
    void ambiguousActiveStudentMembershipCannotWithdrawAppealOrCloseFeedback() throws Exception {
        insertMembership(studentA, schoolB, "STUDENT");
        RequestPostProcessor ambiguous = principal(Role.STUDENT, studentA, schoolA, studentAMembership);

        mvc.perform(post("/api/v1/student/appeals/{id}/withdraw", appealA).with(ambiguous).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_SCOPE_DENIED"));
        mvc.perform(post("/api/v1/student/feedback/{id}/close", feedbackA).with(ambiguous).with(csrf())
                        .contentType("application/json").content("{\"reason\":\"done\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_SCOPE_DENIED"));
    }

    @Test
    void studentSubmissionAcceptsScoreAndRejectsRanking() throws Exception {
        RequestPostProcessor student = principal(Role.STUDENT, studentA, schoolA, studentAMembership);
        long before = countAppeals(studentA);

        mvc.perform(post("/api/v1/student/appeals").with(student).with(csrf())
                        .contentType("application/json").content(appealSubmit(scoreA, "SCORE")))
                .andExpect(status().isCreated());
        assertThat(countAppeals(studentA)).isEqualTo(before + 1);

        mvc.perform(post("/api/v1/student/appeals").with(student).with(csrf())
                        .contentType("application/json").content(appealSubmit(scoreA, "RANKING")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        assertThat(countAppeals(studentA)).isEqualTo(before + 1);
    }

    private void assertRoleMatrix(Route route, Role allowed) throws Exception {
        assertThat(mvc.perform(builder(route)).andReturn().getResponse().getStatus())
                .as(route + " anonymous").isEqualTo(401);
        for (Role role : Role.values()) {
            int status = mvc.perform(builder(route).with(principal(role))).andReturn().getResponse().getStatus();
            if (role == allowed) {
                assertThat(status).as(route + " " + role).isNotIn(401, 403);
            } else {
                assertThat(status).as(route + " " + role).isEqualTo(403);
            }
        }
    }

    private MockHttpServletRequestBuilder builder(Route route) {
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(route.method()), route.path());
        if ("POST".equals(route.method())) builder.with(csrf());
        if (route.body() != null) builder.contentType("application/json").content(route.body());
        return builder;
    }

    private RequestPostProcessor principal(Role role) {
        return switch (role) {
            case STUDENT -> principal(role, studentA, schoolA, studentAMembership);
            case SCHOOL_ADMIN -> principal(role, schoolAdminA, schoolA, schoolAdminAMembership);
            case SUPER_ADMIN -> principal(role, superAdmin, null, null);
        };
    }

    private RequestPostProcessor principal(Role role, UUID userId, UUID schoolId, UUID membershipId) {
        List<AuthenticatedSchoolMembership> memberships = schoolId == null
                ? List.of()
                : List.of(new AuthenticatedSchoolMembership(membershipId, schoolId, role.name()));
        var details = new CampusGuinnessUserDetails(userId, "stage21-" + role.name().toLowerCase(),
                "{noop}password", "NORMAL", Set.of(new SimpleGrantedAuthority("ROLE_" + role.name())), memberships);
        return user(details);
    }

    private UUID insertSchool(String label) {
        UUID id = UUID.randomUUID();
        String suffix = id.toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,
                                    contact_name,contact_phone,contact_email,school_status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, "Stage21 Auth School " + label + " " + suffix, "USCC", "ST21-AUTH-" + suffix,
                "ST21-I-" + suffix, "UNIVERSITY", "Region", "Address", "Contact", "13800000000",
                "stage21-" + suffix + "@example.com", "NORMAL");
        return id;
    }

    private UUID insertUser(String label, String platformRole) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)",
                id, "stage21-auth-" + label + "-" + id.toString().substring(0, 8), "{noop}password", "NORMAL",
                platformRole);
        return id;
    }

    private UUID insertMembership(UUID userId, UUID schoolId, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status) VALUES (?,?,?,?,?)",
                id, userId, schoolId, role, "ACTIVE");
        return id;
    }

    private UUID insertScoreGraph(UUID schoolId, UUID studentId, UUID enteredBy, String label) {
        UUID project = UUID.randomUUID();
        UUID rule = UUID.randomUUID();
        UUID activity = UUID.randomUUID();
        UUID activityProject = UUID.randomUUID();
        UUID score = UUID.randomUUID();
        jdbc.update("INSERT INTO challenge_projects(id,name,category,score_storage_type,score_indicator_type,comparison_direction,score_unit,effective_score_rule,project_status,current_rule_version_id) VALUES (?,?,?,?,?,?,?,?,?,?)",
                project, "Stage21 Auth Project " + label + " " + project, "SPORTS", "INTEGER", "NUMERIC",
                "HIGHER_BETTER", "times", "BEST", "PUBLISHED", null);
        jdbc.update("INSERT INTO project_rule_versions(id,project_id,version_number,score_storage_type,score_indicator_type,comparison_direction,score_unit,effective_score_rule,rules_text,created_by) VALUES (?,?,?,?,?,?,?,?,?,?)",
                rule, project, 1, "INTEGER", "NUMERIC", "HIGHER_BETTER", "times", "BEST", "rules", enteredBy);
        jdbc.update("UPDATE challenge_projects SET current_rule_version_id = ? WHERE id = ?", rule, project);
        jdbc.update("INSERT INTO activities(id,school_id,title,execution_status,public_status,created_by) VALUES (?,?,?,?,?,?)",
                activity, schoolId, "Stage21 Auth Activity " + label, "PUBLISHED", "PUBLIC", enteredBy);
        jdbc.update("INSERT INTO activity_projects(id,activity_id,project_id,rule_version_id) VALUES (?,?,?,?)",
                activityProject, activity, project, rule);
        jdbc.update("INSERT INTO score_attempts(id,school_id,activity_project_id,student_id,attempt_number,score_storage_type,score_value,score_status,entered_by,score_business_time) VALUES (?,?,?,?,?,?,?,?,?,?)",
                score, schoolId, activityProject, studentId, 1, "INTEGER", BigDecimal.TEN, "APPROVED", enteredBy,
                java.sql.Timestamp.from(Instant.now()));
        return score;
    }

    private UUID insertAppeal(UUID schoolId, UUID studentId, UUID scoreId, String type, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO score_appeals(id,school_id,score_attempt_id,student_id,appeal_type,appeal_reason,appeal_status) VALUES (?,?,?,?,?,?,?)",
                id, schoolId, scoreId, studentId, type, "Stage21 authorization appeal", status);
        return id;
    }

    private UUID insertFeedback(UUID schoolId, UUID studentId, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO feedbacks(id,school_id,submitter_id,feedback_type,content,feedback_status) VALUES (?,?,?,?,?,?)",
                id, schoolId, studentId, "GENERAL", "Stage21 authorization feedback " + id, status);
        return id;
    }

    private long countAppeals(UUID studentId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM score_appeals WHERE student_id = ?", Long.class, studentId);
    }

    private String appealSubmit(UUID scoreAttemptId, String appealType) {
        return """
                {"scoreAttemptId":"%s","appealType":"%s","appealReason":"Stage21 authorization"}
                """.formatted(scoreAttemptId, appealType);
    }

    private Route getRoute(String path) { return new Route("GET", path, null); }
    private Route postRoute(String path, String body) { return new Route("POST", path, body); }

    private record Route(String method, String path, String body) {}
    private enum Role { STUDENT, SCHOOL_ADMIN, SUPER_ADMIN }
}
