package com.campusguinness.interfaces.web.scoreattempt;

import com.campusguinness.score.ScoreEntryIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class SchoolAdminScoreEntryControllerIT extends ScoreEntryIntegrationTestSupport {
    @Autowired MockMvc mvc;

    @Test
    void unauthenticatedCreateReturns401() throws Exception {
        mvc.perform(post("/api/v1/school-admin/score-attempts/drafts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(integerCreateBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void teacherCannotUseSchoolAdminEntryApi() throws Exception {
        mvc.perform(post("/api/v1/school-admin/score-attempts/drafts")
                        .with(csrf())
                        .with(auth(teacherId, schoolId, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(integerCreateBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotUseSchoolAdminEntryApi() throws Exception {
        mvc.perform(post("/api/v1/school-admin/score-attempts/drafts")
                        .with(csrf())
                        .with(auth(studentId, schoolId, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(integerCreateBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void schoolAdminCanCreateOwnSchoolDraft() throws Exception {
        mvc.perform(post("/api/v1/school-admin/score-attempts/drafts")
                        .with(csrf())
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(integerCreateBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.matchesPattern(
                                "/api/v1/school-admin/score-attempts/[0-9a-f-]+")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.enteredBy").value(adminId.toString()))
                .andExpect(jsonPath("$.attemptNumber").value(1))
                .andExpect(jsonPath("$.currentEffective").value(false));
    }

    @Test
    void schoolAdminCannotCreateOtherSchoolDraft() throws Exception {
        UUID otherActivity = addActivity(otherSchoolId, "Other School Activity", "PUBLISHED");
        UUID otherActivityProject = addActivityProject(otherActivity, baseProjectFixture());

        mvc.perform(post("/api/v1/school-admin/score-attempts/drafts")
                        .with(csrf())
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(integerCreateBody(otherActivityProject, studentId, "")))
                .andExpect(status().isNotFound());
    }

    @Test
    void createRequestDoesNotAcceptSchoolId() throws Exception {
        expectRejectedCreateField("\"schoolId\":\"" + otherSchoolId + "\"");
    }

    @Test
    void createRequestDoesNotAcceptEnteredBy() throws Exception {
        expectRejectedCreateField("\"enteredBy\":\"" + entrantId + "\"");
    }

    @Test
    void createRequestDoesNotAcceptAttemptNumber() throws Exception {
        expectRejectedCreateField("\"attemptNumber\":99");
    }

    @Test
    void schoolAdminCanUpdateOwnDraft() throws Exception {
        UUID draftId = addAttempt(schoolId, activityProjectId, studentId, adminId,
                1, "INTEGER", BigDecimal.valueOf(80), null, null, "DRAFT");

        mvc.perform(patch("/api/v1/school-admin/score-attempts/" + draftId + "/draft")
                        .with(csrf())
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(integerUpdateBody(120)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.integerValue").value(120));
    }

    @Test
    void schoolAdminCannotUpdateOtherAdminDraft() throws Exception {
        UUID draftId = addAttempt(schoolId, activityProjectId, studentId, adminId,
                1, "INTEGER", BigDecimal.valueOf(80), null, null, "DRAFT");

        mvc.perform(patch("/api/v1/school-admin/score-attempts/" + draftId + "/draft")
                        .with(csrf())
                        .with(auth(entrantId, schoolId, "SCHOOL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(integerUpdateBody(120)))
                .andExpect(status().isForbidden());
    }

    @Test
    void schoolAdminCanReviseOwnRejectedScore() throws Exception {
        UUID rejectedId = addAttempt(schoolId, activityProjectId, studentId, adminId,
                1, "INTEGER", BigDecimal.valueOf(80), null, null, "REJECTED");
        jdbc.update("""
                INSERT INTO score_review_records(
                  id,score_attempt_id,reviewer_id,review_result,
                  review_comment,reject_reason,reviewed_at)
                VALUES (?,?,?,?,?,?,?)
                """, UUID.randomUUID(), rejectedId, entrantId, "REJECTED",
                "verify again", "wrong source", ts(Instant.now()));

        mvc.perform(patch("/api/v1/school-admin/score-attempts/" + rejectedId + "/draft")
                        .with(csrf())
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(integerUpdateBody(88)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.reviewHistory[0].rejectReason").value("wrong source"));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM score_review_records WHERE score_attempt_id=?
                """, Integer.class, rejectedId)).isEqualTo(1);
    }

    @Test
    void schoolAdminCanSubmitOwnDraft() throws Exception {
        UUID draftId = addAttempt(schoolId, activityProjectId, studentId, adminId,
                1, "INTEGER", BigDecimal.valueOf(80), null, null, "DRAFT");

        mvc.perform(post("/api/v1/school-admin/score-attempts/" + draftId + "/submit")
                        .with(csrf())
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.currentEffective").value(false));
    }

    @Test
    void submitWithoutOtherReviewerReturns409() throws Exception {
        jdbc.update("""
                UPDATE school_memberships SET status='ENDED'
                WHERE user_id=? AND school_id=? AND role_in_school='SCHOOL_ADMIN'
                """, entrantId, schoolId);
        UUID draftId = addAttempt(schoolId, activityProjectId, studentId, adminId,
                1, "INTEGER", BigDecimal.valueOf(80), null, null, "DRAFT");

        mvc.perform(post("/api/v1/school-admin/score-attempts/" + draftId + "/submit")
                        .with(csrf())
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_ELIGIBLE_SCORE_REVIEWER"));
    }

    @Test
    void invalidValueCombinationReturns400() throws Exception {
        String body = integerCreateBody(
                activityProjectId, studentId, "\"decimalValue\":12.5,");

        mvc.perform(post("/api/v1/school-admin/score-attempts/drafts")
                        .with(csrf())
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void zeroDurationReturns201() throws Exception {
        jdbc.update("""
                UPDATE challenge_projects
                SET score_storage_type='DURATION',
                    score_indicator_type='DURATION_MS',
                    comparison_direction='LOWER_BETTER',
                    decimal_places=NULL
                WHERE id=?
                """, projectId);
        String body = """
                {
                  "activityProjectId":"%s",
                  "studentId":"%s",
                  "durationMs":0,
                  "scoreBusinessTime":"2026-07-30T10:00:00Z",
                  "timeSource":"ON_SITE_RECORD"
                }
                """.formatted(activityProjectId, studentId);

        mvc.perform(post("/api/v1/school-admin/score-attempts/drafts")
                        .with(csrf())
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationMs").value(0))
                .andExpect(jsonPath("$.scoreStorageType").value("DURATION"));
    }

    @Test
    void terminalActivityReturns409() throws Exception {
        jdbc.update("UPDATE activities SET execution_status='ENDED' WHERE id=?", activityId);

        mvc.perform(post("/api/v1/school-admin/score-attempts/drafts")
                        .with(csrf())
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(integerCreateBody()))
                .andExpect(status().isConflict());
    }

    @Test
    void otherSchoolAttemptReturns404() throws Exception {
        UUID draftId = addAttempt(otherSchoolId, activityProjectId, studentId, adminId,
                1, "INTEGER", BigDecimal.valueOf(80), null, null, "DRAFT");

        mvc.perform(patch("/api/v1/school-admin/score-attempts/" + draftId + "/draft")
                        .with(csrf())
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(integerUpdateBody(120)))
                .andExpect(status().isNotFound());
    }

    @Test
    void mineReturnsOnlyCurrentActorEntries() throws Exception {
        UUID own = addAttempt(schoolId, activityProjectId, studentId, adminId,
                1, "INTEGER", BigDecimal.valueOf(80), null, null, "DRAFT");
        addAttempt(schoolId, activityProjectId, studentId, entrantId,
                2, "INTEGER", BigDecimal.valueOf(90), null, null, "DRAFT");

        mvc.perform(get("/api/v1/school-admin/score-attempts/mine")
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].attemptId").value(own.toString()));
    }

    @Test
    void projectOptionsAreSchoolScoped() throws Exception {
        UUID otherActivity = addActivity(otherSchoolId, "Other School Activity", "PUBLISHED");
        addActivityProject(otherActivity, baseProjectFixture());

        mvc.perform(get("/api/v1/school-admin/score-entry/projects")
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].activityProjectId")
                        .value(activityProjectId.toString()));
    }

    @Test
    void participantOptionsAreProjectScoped() throws Exception {
        ProjectFixture otherProject = addProject(
                "Other Participant Project", "INTEGER", "pts", 0, null);
        UUID otherActivityProject = addActivityProject(activityId, otherProject);
        UUID otherStudent = addUser("participant-scope");
        UUID otherMembership = addMembership(
                otherStudent, schoolId, "STUDENT", "ACTIVE");
        UUID otherParticipant = addParticipant(activityId, otherMembership);
        assignParticipant(otherActivityProject, otherParticipant);

        mvc.perform(get("/api/v1/school-admin/score-entry/projects/"
                        + activityProjectId + "/participants")
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].studentId").value(studentId.toString()));
    }

    private void expectRejectedCreateField(String extraField) throws Exception {
        mvc.perform(post("/api/v1/school-admin/score-attempts/drafts")
                        .with(csrf())
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(integerCreateBody(
                                activityProjectId, studentId, extraField + ",")))
                .andExpect(status().isBadRequest());
    }

    private String integerCreateBody() {
        return integerCreateBody(activityProjectId, studentId, "");
    }

    private static String integerCreateBody(
            UUID targetActivityProjectId, UUID targetStudentId, String extra) {
        return """
                {
                  "activityProjectId":"%s",
                  "studentId":"%s",
                  %s
                  "integerValue":100,
                  "scoreBusinessTime":"2026-07-30T10:00:00Z",
                  "timeSource":"ON_SITE_RECORD"
                }
                """.formatted(targetActivityProjectId, targetStudentId, extra);
    }

    private static String integerUpdateBody(long value) {
        return """
                {
                  "integerValue":%d,
                  "scoreBusinessTime":"2026-07-30T10:00:00Z",
                  "timeSource":"TEACHER_CONFIRMED"
                }
                """.formatted(value);
    }
}
