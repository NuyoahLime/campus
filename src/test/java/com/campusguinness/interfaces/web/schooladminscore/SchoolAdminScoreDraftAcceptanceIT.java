package com.campusguinness.interfaces.web.schooladminscore;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.infrastructure.security.AuthenticatedSchoolMembership;
import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class SchoolAdminScoreDraftAcceptanceIT extends PostgreSqlIntegrationTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @PersistenceContext EntityManager entityManager;

    private UUID schoolA;
    private UUID schoolB;
    private UUID adminA;
    private UUID adminB;
    private UUID studentA;
    private UUID studentB;
    private UUID studentC;
    private UUID adminAMembership;
    private UUID adminBMembership;
    private UUID studentAMembership;
    private UUID studentBMembership;
    private UUID studentCMembership;
    private UUID projectA;
    private UUID ruleA;
    private UUID activityA;
    private UUID activityProjectA;
    private UUID projectB;
    private UUID ruleB;
    private UUID activityB;
    private UUID activityProjectB;

    @BeforeEach
    void setUp() {
        schoolA = insertSchool("A");
        schoolB = insertSchool("B");
        adminA = insertUser("admin-a", null);
        adminB = insertUser("admin-b", null);
        studentA = insertUser("student-a", null);
        studentB = insertUser("student-b", null);
        studentC = insertUser("student-c", null);

        adminAMembership = insertMembership(adminA, schoolA, "SCHOOL_ADMIN");
        adminBMembership = insertMembership(adminB, schoolB, "SCHOOL_ADMIN");
        studentAMembership = insertMembership(studentA, schoolA, "STUDENT");
        studentBMembership = insertMembership(studentB, schoolA, "STUDENT");
        studentCMembership = insertMembership(studentC, schoolB, "STUDENT");
        insertStudentProfile(studentAMembership, "2026", "1 班", "SCORE-001");
        insertStudentProfile(studentBMembership, "2026", "2 班", "SCORE-002");
        insertStudentProfile(studentCMembership, "2026", "3 班", "SCORE-003");

        projectA = insertProject("A");
        ruleA = insertRuleVersion(projectA, adminA);
        activityA = insertActivity(schoolA, adminA, "Score Draft Activity A");
        activityProjectA = insertActivityProject(activityA, projectA, ruleA);
        insertParticipant(activityA, studentAMembership);

        projectB = insertProject("B");
        ruleB = insertRuleVersion(projectB, adminB);
        activityB = insertActivity(schoolB, adminB, "Score Draft Activity B");
        activityProjectB = insertActivityProject(activityB, projectB, ruleB);
        insertParticipant(activityB, studentCMembership);
    }

    @Test
    void sameSchoolParticipantCanCreateEditAndRefreshDraftScores() throws Exception {
        RequestPostProcessor admin = principal(adminA, schoolA, adminAMembership, "SCHOOL_ADMIN");

        mvc.perform(get("/api/v1/school-admin/activities/{id}/score-candidates", activityA).with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityId").value(activityA.toString()))
                .andExpect(jsonPath("$.candidates[0].studentId").value(studentA.toString()))
                .andExpect(jsonPath("$.candidates[0].projects[0].activityProjectId").value(activityProjectA.toString()));

        mvc.perform(post("/api/v1/school-admin/activity-projects/{id}/score-attempts", activityProjectA)
                        .with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scoreCreateBody(studentA, 7L, Instant.parse("2026-08-25T01:00:00Z"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.integerValue").value(7))
                .andExpect(jsonPath("$.studentId").value(studentA.toString()));

        UUID scoreId = findScoreAttempt(activityProjectA, studentA);

        mvc.perform(get("/api/v1/school-admin/score-attempts/{id}", scoreId).with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scoreAttemptId").value(scoreId.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.studentId").value(studentA.toString()));

        mvc.perform(patch("/api/v1/school-admin/score-attempts/{id}", scoreId)
                        .with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scoreUpdateBody(9L, Instant.parse("2026-08-25T02:00:00Z"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.integerValue").value(9))
                .andExpect(jsonPath("$.studentId").value(studentA.toString()));

        mvc.perform(get("/api/v1/school-admin/activities/{id}/scores", activityA).with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scores[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.scores[0].integerValue").value(9));

        setScoreStatus(scoreId, "APPROVED");
        entityManager.clear();

        mvc.perform(patch("/api/v1/school-admin/score-attempts/{id}", scoreId)
                        .with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scoreUpdateBody(11L, Instant.parse("2026-08-25T03:00:00Z"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCORE_INVALID_STATE_TRANSITION"));
    }

    @Test
    void sameSchoolAdminCannotCreateDraftForNonParticipantStudent() throws Exception {
        RequestPostProcessor admin = principal(adminA, schoolA, adminAMembership, "SCHOOL_ADMIN");

        mvc.perform(post("/api/v1/school-admin/activity-projects/{id}/score-attempts", activityProjectA)
                        .with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scoreCreateBody(studentB, 5L, Instant.parse("2026-08-25T01:00:00Z"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCORE_STUDENT_NOT_PARTICIPANT"));
    }

    @Test
    void schoolScopeAndRoleBoundariesRejectWrites() throws Exception {
        mvc.perform(post("/api/v1/school-admin/activity-projects/{id}/score-attempts", activityProjectA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scoreCreateBody(studentA, 5L, Instant.parse("2026-08-25T01:00:00Z"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mvc.perform(post("/api/v1/school-admin/activity-projects/{id}/score-attempts", activityProjectA)
                        .with(principal(studentA, schoolA, studentAMembership, "STUDENT")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scoreCreateBody(studentA, 5L, Instant.parse("2026-08-25T01:00:00Z"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(post("/api/v1/school-admin/activity-projects/{id}/score-attempts", activityProjectA)
                        .with(principal(UUID.randomUUID(), null, null, "SUPER_ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scoreCreateBody(studentA, 5L, Instant.parse("2026-08-25T01:00:00Z"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(post("/api/v1/school-admin/activity-projects/{id}/score-attempts", activityProjectA)
                        .with(principal(UUID.randomUUID(), null, null, "TEACHER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scoreCreateBody(studentA, 5L, Instant.parse("2026-08-25T01:00:00Z"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(post("/api/v1/school-admin/activity-projects/{id}/score-attempts", activityProjectB)
                        .with(principal(adminA, schoolA, adminAMembership, "SCHOOL_ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scoreCreateBody(studentC, 5L, Instant.parse("2026-08-25T01:00:00Z"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCORE_SCOPE_DENIED"));

        mvc.perform(patch("/api/v1/school-admin/score-attempts/{id}", UUID.randomUUID())
                        .with(principal(studentA, schoolA, studentAMembership, "STUDENT")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scoreUpdateBody(7L, Instant.parse("2026-08-25T02:00:00Z"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void inactiveOrAmbiguousSchoolAdminMembershipCannotWriteScores() throws Exception {
        UUID inactiveAdmin = insertUser("inactive-admin", null);
        UUID inactiveMembership = insertMembership(inactiveAdmin, schoolA, "SCHOOL_ADMIN", "ENDED");

        mvc.perform(post("/api/v1/school-admin/activity-projects/{id}/score-attempts", activityProjectA)
                        .with(principal(inactiveAdmin, schoolA, inactiveMembership, "SCHOOL_ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scoreCreateBody(studentA, 5L, Instant.parse("2026-08-25T01:00:00Z"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCORE_SCOPE_DENIED"));

        UUID ambiguousAdmin = insertUser("ambiguous-admin", null);
        UUID ambiguousMembershipA = insertMembership(ambiguousAdmin, schoolA, "SCHOOL_ADMIN");
        insertMembership(ambiguousAdmin, schoolB, "SCHOOL_ADMIN");

        mvc.perform(post("/api/v1/school-admin/activity-projects/{id}/score-attempts", activityProjectA)
                        .with(principal(ambiguousAdmin, schoolA, ambiguousMembershipA, "SCHOOL_ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scoreCreateBody(studentA, 5L, Instant.parse("2026-08-25T01:00:00Z"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCORE_SCOPE_DENIED"));
    }

    @Test
    void sameSchoolAdminCanSubmitRejectAndReturnDraftWithReviewHistory() throws Exception {
        RequestPostProcessor admin = principal(adminA, schoolA, adminAMembership, "SCHOOL_ADMIN");
        UUID scoreId = createDraft(admin, studentA);

        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/submit", scoreId)
                        .with(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        Map<String, Object> submitted = jdbc.queryForMap(
                "SELECT score_status, submitted_at, is_current_effective, entered_by "
                        + "FROM score_attempts WHERE id = ?", scoreId);
        assertThat(submitted.get("score_status")).isEqualTo("PENDING_REVIEW");
        assertThat(submitted.get("submitted_at")).isNotNull();
        assertThat(submitted.get("is_current_effective")).isEqualTo(false);
        assertThat(submitted.get("entered_by")).isEqualTo(adminA);
        entityManager.flush();
        Map<String, Object> submissionAudit = jdbc.queryForMap(
                "SELECT actor_id, action, target_type, target_id FROM audit_records "
                        + "WHERE action = 'SCORE_ATTEMPT_SUBMITTED' AND target_id = ?", scoreId);
        assertThat(submissionAudit.get("actor_id")).isEqualTo(adminA);
        assertThat(submissionAudit.get("action")).isEqualTo("SCORE_ATTEMPT_SUBMITTED");
        assertThat(submissionAudit.get("target_type")).isEqualTo("SCORE_ATTEMPT");
        assertThat(submissionAudit.get("target_id")).isEqualTo(scoreId);

        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/reject", scoreId)
                        .with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Needs evidence\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        Map<String, Object> review = jdbc.queryForMap(
                "SELECT reviewer_id, review_result, reject_reason "
                        + "FROM score_review_records WHERE score_attempt_id = ?", scoreId);
        assertThat(review.get("reviewer_id")).isEqualTo(adminA);
        assertThat(review.get("review_result")).isEqualTo("REJECTED");
        assertThat(review.get("reject_reason")).isEqualTo("Needs evidence");

        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/return-to-draft", scoreId)
                        .with(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        Map<String, Object> returned = jdbc.queryForMap(
                "SELECT score_status, submitted_at, is_current_effective "
                        + "FROM score_attempts WHERE id = ?", scoreId);
        assertThat(returned.get("score_status")).isEqualTo("DRAFT");
        assertThat(returned.get("submitted_at")).isNull();
        assertThat(returned.get("is_current_effective")).isEqualTo(false);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM score_review_records WHERE score_attempt_id = ?",
                Integer.class, scoreId)).isEqualTo(1);
    }

    @Test
    void lifecycleRejectsDuplicateAndInvalidTransitions() throws Exception {
        RequestPostProcessor admin = principal(adminA, schoolA, adminAMembership, "SCHOOL_ADMIN");
        UUID scoreId = createDraft(admin, studentA);

        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/return-to-draft", scoreId)
                        .with(admin).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCORE_INVALID_STATE_TRANSITION"));

        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/submit", scoreId)
                        .with(admin).with(csrf()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/submit", scoreId)
                        .with(admin).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCORE_INVALID_STATE_TRANSITION"));

        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/reject", scoreId)
                        .with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void lifecycleEnforcesAuthenticationRoleAndSchoolScope() throws Exception {
        RequestPostProcessor admin = principal(adminA, schoolA, adminAMembership, "SCHOOL_ADMIN");
        UUID scoreId = createDraft(admin, studentA);

        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/submit", scoreId).with(csrf()))
                .andExpect(status().isUnauthorized());

        for (String role : List.of("STUDENT", "SUPER_ADMIN", "TEACHER")) {
            mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/submit", scoreId)
                            .with(principal(UUID.randomUUID(), null, null, role)).with(csrf()))
                    .andExpect(status().isForbidden());
        }

        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/submit", scoreId)
                        .with(principal(adminB, schoolB, adminBMembership, "SCHOOL_ADMIN")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCORE_SCOPE_DENIED"));

        UUID inactiveAdmin = insertUser("lifecycle-inactive-admin", null);
        UUID inactiveMembership = insertMembership(inactiveAdmin, schoolA, "SCHOOL_ADMIN", "ENDED");
        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/submit", scoreId)
                        .with(principal(inactiveAdmin, schoolA, inactiveMembership, "SCHOOL_ADMIN")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCORE_SCOPE_DENIED"));

        UUID ambiguousAdmin = insertUser("lifecycle-ambiguous-admin", null);
        UUID ambiguousMembership = insertMembership(ambiguousAdmin, schoolA, "SCHOOL_ADMIN");
        insertMembership(ambiguousAdmin, schoolB, "SCHOOL_ADMIN");
        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/submit", scoreId)
                        .with(principal(ambiguousAdmin, schoolA, ambiguousMembership, "SCHOOL_ADMIN")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCORE_SCOPE_DENIED"));
    }

    @Test
    void sameSchoolAdminCanApprovePersistReviewAndSelectBestEffectiveScore() throws Exception {
        RequestPostProcessor admin = principal(adminA, schoolA, adminAMembership, "SCHOOL_ADMIN");
        UUID scoreId = createDraft(admin, studentA);

        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/submit", scoreId)
                        .with(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/approve", scoreId)
                        .with(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        Map<String, Object> score = jdbc.queryForMap(
                "SELECT score_status, is_current_effective FROM score_attempts WHERE id = ?", scoreId);
        assertThat(score.get("score_status")).isEqualTo("APPROVED");
        assertThat(score.get("is_current_effective")).isEqualTo(true);

        Map<String, Object> review = jdbc.queryForMap(
                "SELECT reviewer_id, review_result, reviewed_at, review_comment, reject_reason "
                        + "FROM score_review_records WHERE score_attempt_id = ?", scoreId);
        assertThat(review.get("reviewer_id")).isEqualTo(adminA);
        assertThat(review.get("review_result")).isEqualTo("APPROVED");
        assertThat(review.get("reviewed_at")).isNotNull();
        assertThat(review.get("review_comment")).isNull();
        assertThat(review.get("reject_reason")).isNull();
    }

    @Test
    void approveEnforcesAuthenticationRoleAndSchoolScope() throws Exception {
        RequestPostProcessor admin = principal(adminA, schoolA, adminAMembership, "SCHOOL_ADMIN");
        UUID scoreId = createDraft(admin, studentA);
        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/submit", scoreId)
                        .with(admin).with(csrf()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/approve", scoreId).with(csrf()))
                .andExpect(status().isUnauthorized());

        for (String role : List.of("STUDENT", "SUPER_ADMIN", "TEACHER")) {
            mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/approve", scoreId)
                            .with(principal(UUID.randomUUID(), null, null, role)).with(csrf()))
                    .andExpect(status().isForbidden());
        }

        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/approve", scoreId)
                        .with(principal(adminB, schoolB, adminBMembership, "SCHOOL_ADMIN")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCORE_SCOPE_DENIED"));

        UUID inactiveAdmin = insertUser("approve-inactive-admin", null);
        UUID inactiveMembership = insertMembership(inactiveAdmin, schoolA, "SCHOOL_ADMIN", "ENDED");
        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/approve", scoreId)
                        .with(principal(inactiveAdmin, schoolA, inactiveMembership, "SCHOOL_ADMIN")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCORE_SCOPE_DENIED"));

        UUID ambiguousAdmin = insertUser("approve-ambiguous-admin", null);
        UUID ambiguousMembership = insertMembership(ambiguousAdmin, schoolA, "SCHOOL_ADMIN");
        insertMembership(ambiguousAdmin, schoolB, "SCHOOL_ADMIN");
        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/approve", scoreId)
                        .with(principal(ambiguousAdmin, schoolA, ambiguousMembership, "SCHOOL_ADMIN")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCORE_SCOPE_DENIED"));
    }

    @Test
    void approveRejectsEveryNonPendingStateWithConflict() throws Exception {
        RequestPostProcessor admin = principal(adminA, schoolA, adminAMembership, "SCHOOL_ADMIN");

        UUID draft = createDraft(admin, studentA);
        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/approve", draft)
                        .with(admin).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCORE_INVALID_STATE_TRANSITION"));

        UUID rejected = createDraft(admin, studentA);
        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/submit", rejected)
                        .with(admin).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/reject", rejected)
                        .with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Needs revision\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/approve", rejected)
                        .with(admin).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCORE_INVALID_STATE_TRANSITION"));

        UUID approved = createDraft(admin, studentA);
        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/submit", approved)
                        .with(admin).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/approve", approved)
                        .with(admin).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/approve", approved)
                        .with(admin).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCORE_INVALID_STATE_TRANSITION"));

        UUID invalidated = createDraft(admin, studentA);
        setScoreStatus(invalidated, "INVALIDATED");
        entityManager.clear();
        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/approve", invalidated)
                        .with(admin).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCORE_INVALID_STATE_TRANSITION"));
    }

    @Test
    void concurrentApprovalsAllowOneSuccessAndOneConflict() throws Exception {
        RequestPostProcessor admin = principal(adminA, schoolA, adminAMembership, "SCHOOL_ADMIN");
        UUID scoreId = createDraft(admin, studentA);
        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/submit", scoreId)
                        .with(admin).with(csrf()))
                .andExpect(status().isOk());

        TestTransaction.flagForCommit();
        TestTransaction.end();

        var start = new CountDownLatch(1);
        try {
            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> approveStatus(admin, scoreId, start));
                var second = executor.submit(() -> approveStatus(admin, scoreId, start));
                start.countDown();
                assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
            }

            Map<String, Object> score = jdbc.queryForMap(
                    "SELECT score_status, is_current_effective FROM score_attempts WHERE id = ?", scoreId);
            assertThat(score.get("score_status")).isEqualTo("APPROVED");
            assertThat(score.get("is_current_effective")).isEqualTo(true);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM score_review_records WHERE score_attempt_id = ?",
                    Integer.class, scoreId)).isEqualTo(1);
        } finally {
            cleanupCommittedFixture();
        }
    }

    @Test
    void concurrentBestApprovalsChooseTheDeterministicBestAndKeepOneEffective() throws Exception {
        RequestPostProcessor admin = principal(adminA, schoolA, adminAMembership, "SCHOOL_ADMIN");
        UUID lower = createDraft(admin, studentA, 7L);
        UUID higher = createDraft(admin, studentA, 9L);
        submit(admin, lower);
        submit(admin, higher);

        TestTransaction.flagForCommit();
        TestTransaction.end();

        var start = new CountDownLatch(1);
        try {
            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> approveStatus(admin, lower, start));
                var second = executor.submit(() -> approveStatus(admin, higher, start));
                start.countDown();
                assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 200);
            }
            assertThat(jdbc.queryForObject("""
                    SELECT id FROM score_attempts
                    WHERE activity_project_id = ? AND student_id = ? AND is_current_effective = true
                    """, UUID.class, activityProjectA, studentA)).isEqualTo(higher);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM score_attempts
                    WHERE activity_project_id = ? AND student_id = ? AND is_current_effective = true
                    """, Integer.class, activityProjectA, studentA)).isEqualTo(1);
        } finally {
            cleanupCommittedFixture();
        }
    }

    @Test
    void concurrentLastApprovalsChooseHighestAttemptNumber() throws Exception {
        RequestPostProcessor admin = principal(adminA, schoolA, adminAMembership, "SCHOOL_ADMIN");
        jdbc.update("UPDATE project_rule_versions SET effective_score_rule = 'LAST' WHERE id = ?", ruleA);
        UUID firstAttempt = createDraft(admin, studentA, 99L);
        UUID lastAttempt = createDraft(admin, studentA, 1L);
        submit(admin, firstAttempt);
        submit(admin, lastAttempt);

        TestTransaction.flagForCommit();
        TestTransaction.end();

        var start = new CountDownLatch(1);
        try {
            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> approveStatus(admin, firstAttempt, start));
                var second = executor.submit(() -> approveStatus(admin, lastAttempt, start));
                start.countDown();
                assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 200);
            }
            assertThat(jdbc.queryForObject("""
                    SELECT id FROM score_attempts
                    WHERE activity_project_id = ? AND student_id = ? AND is_current_effective = true
                    """, UUID.class, activityProjectA, studentA)).isEqualTo(lastAttempt);
        } finally {
            cleanupCommittedFixture();
        }
    }

    @Test
    void adminDesignatedEndpointRequiresCasAndSchoolAdminScope() throws Exception {
        RequestPostProcessor admin = principal(adminA, schoolA, adminAMembership, "SCHOOL_ADMIN");
        jdbc.update("UPDATE project_rule_versions SET effective_score_rule = 'ADMIN_DESIGNATED' WHERE id = ?", ruleA);
        UUID first = createDraft(admin, studentA, 7L);
        UUID second = createDraft(admin, studentA, 9L);
        submit(admin, first);
        submit(admin, second);
        approve(admin, first);
        approve(admin, second);

        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/designate-effective", first).with(csrf()))
                .andExpect(status().isUnauthorized());
        for (String role : List.of("STUDENT", "SUPER_ADMIN", "TEACHER")) {
            mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/designate-effective", first)
                            .with(principal(UUID.randomUUID(), null, null, role)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedCurrentEffectiveAttemptId\":null}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/designate-effective", first)
                        .with(principal(adminB, schoolB, adminBMembership, "SCHOOL_ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedCurrentEffectiveAttemptId\":null}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCORE_SCOPE_DENIED"));

        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/designate-effective", first)
                        .with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedCurrentEffectiveAttemptId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentEffective").value(true));
        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/designate-effective", second)
                        .with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentEffectiveAttemptId\":null}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCORE_EFFECTIVE_CONFLICT"));
        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/designate-effective", first)
                        .with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentEffectiveAttemptId\":\"" + first + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentEffective").value(true));
    }

    @Test
    void concurrentAdminDesignationUsesCasAndLeavesOneEffectiveScore() throws Exception {
        RequestPostProcessor admin = principal(adminA, schoolA, adminAMembership, "SCHOOL_ADMIN");
        jdbc.update("UPDATE project_rule_versions SET effective_score_rule = 'ADMIN_DESIGNATED' WHERE id = ?", ruleA);
        UUID first = createDraft(admin, studentA, 7L);
        UUID second = createDraft(admin, studentA, 9L);
        submit(admin, first);
        submit(admin, second);
        approve(admin, first);
        approve(admin, second);

        TestTransaction.flagForCommit();
        TestTransaction.end();

        var start = new CountDownLatch(1);
        try {
            try (var executor = Executors.newFixedThreadPool(2)) {
                var firstResult = executor.submit(() -> designateStatus(admin, first, null, start));
                var secondResult = executor.submit(() -> designateStatus(admin, second, null, start));
                start.countDown();
                assertThat(List.of(firstResult.get(), secondResult.get())).containsExactlyInAnyOrder(200, 409);
            }
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM score_attempts
                    WHERE activity_project_id = ? AND student_id = ? AND is_current_effective = true
                    """, Integer.class, activityProjectA, studentA)).isEqualTo(1);
        } finally {
            cleanupCommittedFixture();
        }
    }

    @Test
    void sameSchoolAdminCanReadChronologicalReviewHistoryWithoutMutation() throws Exception {
        RequestPostProcessor admin = principal(adminA, schoolA, adminAMembership, "SCHOOL_ADMIN");
        UUID scoreId = createDraft(admin, studentA);

        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/submit", scoreId)
                        .with(admin).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/reject", scoreId)
                        .with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Needs evidence\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/return-to-draft", scoreId)
                        .with(admin).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/submit", scoreId)
                        .with(admin).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/approve", scoreId)
                        .with(admin).with(csrf()))
                .andExpect(status().isOk());

        Instant rejectedAt = Instant.parse("2026-08-25T01:00:00Z");
        Instant approvedAt = Instant.parse("2026-08-25T02:00:00Z");
        jdbc.update("UPDATE score_review_records SET reviewed_at = ? WHERE score_attempt_id = ? AND review_result = 'REJECTED'",
                Timestamp.from(rejectedAt), scoreId);
        jdbc.update("UPDATE score_review_records SET reviewed_at = ? WHERE score_attempt_id = ? AND review_result = 'APPROVED'",
                Timestamp.from(approvedAt), scoreId);
        int reviewCountBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM score_review_records WHERE score_attempt_id = ?", Integer.class, scoreId);
        String reviewerUsername = jdbc.queryForObject("SELECT username FROM users WHERE id = ?", String.class, adminA);

        mvc.perform(get("/api/v1/school-admin/score-attempts/{id}/reviews", scoreId).with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scoreAttemptId").value(scoreId.toString()))
                .andExpect(jsonPath("$.reviews.length()").value(2))
                .andExpect(jsonPath("$.reviews[0].result").value("REJECTED"))
                .andExpect(jsonPath("$.reviews[0].reviewerId").value(adminA.toString()))
                .andExpect(jsonPath("$.reviews[0].reviewerUsername").value(reviewerUsername))
                .andExpect(jsonPath("$.reviews[0].rejectReason").value("Needs evidence"))
                .andExpect(jsonPath("$.reviews[0].reviewComment").value(nullValue()))
                .andExpect(jsonPath("$.reviews[1].result").value("APPROVED"))
                .andExpect(jsonPath("$.reviews[1].rejectReason").value(nullValue()))
                .andExpect(jsonPath("$.reviews[1].reviewerUsername").value(reviewerUsername))
                .andExpect(jsonPath("$.reviews[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.reviews[0].accountStatus").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM score_review_records WHERE score_attempt_id = ?",
                Integer.class, scoreId)).isEqualTo(reviewCountBefore);
        Map<String, Object> score = jdbc.queryForMap(
                "SELECT score_status, is_current_effective FROM score_attempts WHERE id = ?", scoreId);
        assertThat(score.get("score_status")).isEqualTo("APPROVED");
        assertThat(score.get("is_current_effective")).isEqualTo(true);
    }

    @Test
    void validScoreAttemptWithoutReviewsReturnsEmptyHistory() throws Exception {
        RequestPostProcessor admin = principal(adminA, schoolA, adminAMembership, "SCHOOL_ADMIN");
        UUID scoreId = createDraft(admin, studentA);

        mvc.perform(get("/api/v1/school-admin/score-attempts/{id}/reviews", scoreId).with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scoreAttemptId").value(scoreId.toString()))
                .andExpect(jsonPath("$.reviews.length()").value(0));
    }

    @Test
    void reviewHistoryEnforcesAuthenticationRoleAndSchoolScope() throws Exception {
        RequestPostProcessor admin = principal(adminA, schoolA, adminAMembership, "SCHOOL_ADMIN");
        UUID scoreId = createDraft(admin, studentA);

        mvc.perform(get("/api/v1/school-admin/score-attempts/{id}/reviews", scoreId))
                .andExpect(status().isUnauthorized());

        for (String role : List.of("STUDENT", "SUPER_ADMIN", "TEACHER")) {
            mvc.perform(get("/api/v1/school-admin/score-attempts/{id}/reviews", scoreId)
                            .with(principal(UUID.randomUUID(), null, null, role)))
                    .andExpect(status().isForbidden());
        }

        mvc.perform(get("/api/v1/school-admin/score-attempts/{id}/reviews", scoreId)
                        .with(principal(adminB, schoolB, adminBMembership, "SCHOOL_ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCORE_ATTEMPT_NOT_FOUND"));

        UUID inactiveAdmin = insertUser("history-inactive-admin", null);
        UUID inactiveMembership = insertMembership(inactiveAdmin, schoolA, "SCHOOL_ADMIN", "ENDED");
        mvc.perform(get("/api/v1/school-admin/score-attempts/{id}/reviews", scoreId)
                        .with(principal(inactiveAdmin, schoolA, inactiveMembership, "SCHOOL_ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCORE_SCOPE_DENIED"));

        UUID ambiguousAdmin = insertUser("history-ambiguous-admin", null);
        UUID ambiguousMembership = insertMembership(ambiguousAdmin, schoolA, "SCHOOL_ADMIN");
        insertMembership(ambiguousAdmin, schoolB, "SCHOOL_ADMIN");
        mvc.perform(get("/api/v1/school-admin/score-attempts/{id}/reviews", scoreId)
                        .with(principal(ambiguousAdmin, schoolA, ambiguousMembership, "SCHOOL_ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCORE_SCOPE_DENIED"));
    }

    @Test
    void reviewHistoryReturnsNotFoundForUnknownAttempt() throws Exception {
        RequestPostProcessor admin = principal(adminA, schoolA, adminAMembership, "SCHOOL_ADMIN");

        mvc.perform(get("/api/v1/school-admin/score-attempts/{id}/reviews", UUID.randomUUID()).with(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCORE_ATTEMPT_NOT_FOUND"));
    }

    private int approveStatus(RequestPostProcessor admin, UUID scoreId, CountDownLatch start) throws Exception {
        start.await();
        return mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/approve", scoreId)
                        .with(admin).with(csrf())).andReturn().getResponse().getStatus();
    }

    private int designateStatus(RequestPostProcessor admin, UUID scoreId, UUID expectedCurrentEffectiveAttemptId,
                                CountDownLatch start) throws Exception {
        start.await();
        String expected = expectedCurrentEffectiveAttemptId == null
                ? "null"
                : "\"" + expectedCurrentEffectiveAttemptId + "\"";
        return mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/designate-effective", scoreId)
                        .with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentEffectiveAttemptId\":" + expected + "}"))
                .andReturn().getResponse().getStatus();
    }

    private void submit(RequestPostProcessor admin, UUID scoreId) throws Exception {
        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/submit", scoreId)
                        .with(admin).with(csrf()))
                .andExpect(status().isOk());
    }

    private void approve(RequestPostProcessor admin, UUID scoreId) throws Exception {
        mvc.perform(post("/api/v1/school-admin/score-attempts/{id}/approve", scoreId)
                        .with(admin).with(csrf()))
                .andExpect(status().isOk());
    }

    private void cleanupCommittedFixture() {
        jdbc.update("DELETE FROM score_review_records WHERE score_attempt_id IN "
                + "(SELECT id FROM score_attempts WHERE school_id IN (?, ?))", schoolA, schoolB);
        jdbc.update("DELETE FROM audit_records WHERE school_id IN (?, ?)", schoolA, schoolB);
        jdbc.update("DELETE FROM score_attempts WHERE school_id IN (?, ?)", schoolA, schoolB);
        jdbc.update("DELETE FROM activity_participants WHERE activity_id IN (?, ?)", activityA, activityB);
        jdbc.update("DELETE FROM activity_projects WHERE activity_id IN (?, ?)", activityA, activityB);
        jdbc.update("DELETE FROM activities WHERE id IN (?, ?)", activityA, activityB);
        jdbc.update("UPDATE challenge_projects SET current_rule_version_id = NULL WHERE id IN (?, ?)",
                projectA, projectB);
        jdbc.update("DELETE FROM project_rule_versions WHERE id IN (?, ?)", ruleA, ruleB);
        jdbc.update("DELETE FROM challenge_projects WHERE id IN (?, ?)", projectA, projectB);
        jdbc.update("DELETE FROM student_profiles WHERE membership_id IN (?, ?, ?)",
                studentAMembership, studentBMembership, studentCMembership);
        jdbc.update("DELETE FROM school_memberships WHERE id IN (?, ?, ?, ?, ?)",
                adminAMembership, adminBMembership, studentAMembership,
                studentBMembership, studentCMembership);
        jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?, ?, ?)",
                adminA, adminB, studentA, studentB, studentC);
        jdbc.update("DELETE FROM schools WHERE id IN (?, ?)", schoolA, schoolB);
    }

    private UUID createDraft(RequestPostProcessor admin, UUID studentId) throws Exception {
        return createDraft(admin, studentId, 7L);
    }

    private UUID createDraft(RequestPostProcessor admin, UUID studentId, long integerValue) throws Exception {
        mvc.perform(post("/api/v1/school-admin/activity-projects/{id}/score-attempts", activityProjectA)
                        .with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scoreCreateBody(studentId, integerValue, Instant.parse("2026-08-25T01:00:00Z"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));
        return findScoreAttempt(activityProjectA, studentId);
    }

    private RequestPostProcessor principal(UUID userId, UUID schoolId, UUID membershipId, String role) {
        List<AuthenticatedSchoolMembership> memberships = schoolId == null
                ? List.of()
                : List.of(new AuthenticatedSchoolMembership(membershipId, schoolId, role));
        var details = new CampusGuinnessUserDetails(
                userId,
                "stage26-" + role.toLowerCase() + "-" + userId.toString().substring(0, 8),
                "{noop}password",
                "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_" + role)),
                memberships
        );
        return user(details);
    }

    private String scoreCreateBody(UUID studentId, Long integerValue, Instant businessTime) {
        return """
                {
                  "studentId": "%s",
                  "integerValue": %s,
                  "scoreBusinessTime": "%s"
                }
                """.formatted(studentId, integerValue, businessTime);
    }

    private String scoreUpdateBody(Long integerValue, Instant businessTime) {
        return """
                {
                  "integerValue": %s,
                  "scoreBusinessTime": "%s"
                }
                """.formatted(integerValue, businessTime);
    }

    private UUID insertSchool(String label) {
        UUID id = UUID.randomUUID();
        String suffix = id.toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,
                                    contact_name,contact_phone,contact_email,school_status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, "Stage26 Score School " + label + " " + suffix, "USCC", "ST26-SCORE-" + suffix,
                "ST26-S-I-" + suffix, "UNIVERSITY", "Region", "Address", "Contact", "13800000000",
                "stage26-score-" + suffix + "@example.com", "NORMAL");
        return id;
    }

    private UUID insertUser(String label, String platformRole) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)",
                id, "stage26-score-" + label + "-" + id.toString().substring(0, 8), "{noop}password", "NORMAL",
                platformRole);
        return id;
    }

    private UUID insertMembership(UUID userId, UUID schoolId, String role) {
        return insertMembership(userId, schoolId, role, "ACTIVE");
    }

    private UUID insertMembership(UUID userId, UUID schoolId, String role, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status) VALUES (?,?,?,?,?)",
                id, userId, schoolId, role, status);
        return id;
    }

    private void insertStudentProfile(UUID membershipId, String grade, String className, String studentNumber) {
        jdbc.update("INSERT INTO student_profiles(id,membership_id,grade,class_name,student_number) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), membershipId, grade, className, studentNumber);
    }

    private UUID insertProject(String label) {
        UUID projectId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO challenge_projects(id,name,category,score_storage_type,score_indicator_type,
                                               comparison_direction,score_unit,effective_score_rule,project_status,
                                               current_rule_version_id)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """, projectId, "Stage26 Score Project " + label, "SPORTS", "INTEGER", "NUMERIC",
                "HIGHER_BETTER", "次", "BEST", "PUBLISHED", null);
        return projectId;
    }

    private UUID insertRuleVersion(UUID projectId, UUID createdBy) {
        UUID ruleId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO project_rule_versions(id,project_id,version_number,score_storage_type,score_indicator_type,
                                                 comparison_direction,score_unit,decimal_places,grade_order,rules_text,
                                                 venue_requirements,equipment_requirements,effective_score_rule,created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, ruleId, projectId, 1, "INTEGER", "NUMERIC", "HIGHER_BETTER", "次", null, null,
                "Stage26 score rules", null, null, "BEST", createdBy);
        jdbc.update("UPDATE challenge_projects SET current_rule_version_id = ? WHERE id = ?", ruleId, projectId);
        return ruleId;
    }

    private UUID insertActivity(UUID schoolId, UUID createdBy, String title) {
        UUID activityId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO activities(id,school_id,title,execution_status,public_status,created_by)
                VALUES (?,?,?,?,?,?)
                """, activityId, schoolId, title, "PUBLISHED", "PUBLIC", createdBy);
        return activityId;
    }

    private UUID insertActivityProject(UUID activityId, UUID projectId, UUID ruleVersionId) {
        UUID activityProjectId = UUID.randomUUID();
        jdbc.update("INSERT INTO activity_projects(id,activity_id,project_id,rule_version_id) VALUES (?,?,?,?)",
                activityProjectId, activityId, projectId, ruleVersionId);
        return activityProjectId;
    }

    private void insertParticipant(UUID activityId, UUID studentMembershipId) {
        jdbc.update("INSERT INTO activity_participants(id,activity_id,student_membership_id) VALUES (?,?,?)",
                UUID.randomUUID(), activityId, studentMembershipId);
    }

    private UUID findScoreAttempt(UUID activityProjectId, UUID studentId) {
        return jdbc.queryForObject("""
                SELECT id FROM score_attempts
                WHERE activity_project_id = ? AND student_id = ?
                ORDER BY attempt_number DESC, id DESC LIMIT 1
                """, UUID.class, activityProjectId, studentId);
    }

    private void setScoreStatus(UUID scoreAttemptId, String status) {
        jdbc.update("UPDATE score_attempts SET score_status = ?, updated_at = now() WHERE id = ?",
                status, scoreAttemptId);
    }
}
