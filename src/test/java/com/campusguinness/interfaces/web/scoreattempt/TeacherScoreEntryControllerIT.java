package com.campusguinness.interfaces.web.scoreattempt;

import com.campusguinness.score.ScoreEntryIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
class TeacherScoreEntryControllerIT extends ScoreEntryIntegrationTestSupport {
    @Autowired MockMvc mvc;

    private UUID responsibleAssignmentId;

    @BeforeEach
    void assignResponsibleTeacher() {
        UUID membershipId = jdbc.queryForObject("""
                SELECT id FROM school_memberships
                WHERE user_id=? AND school_id=? AND role_in_school='TEACHER'
                """, UUID.class, teacherId, schoolId);
        responsibleAssignmentId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO responsible_teachers(
                  id,activity_project_id,teacher_membership_id,created_at)
                VALUES (?,?,?,?)
                """, responsibleAssignmentId, activityProjectId,
                membershipId, ts(Instant.now()));
    }

    @AfterEach
    void removeResponsibleTeacher() {
        jdbc.update(
                "DELETE FROM responsible_teachers WHERE id=?",
                responsibleAssignmentId);
    }

    @Test
    void teacherCanSubmitScore() throws Exception {
        mvc.perform(post("/api/v1/teacher/score-attempts")
                        .with(csrf())
                        .with(auth(teacherId, schoolId, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(integerCreateBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.matchesPattern(
                                "/api/v1/teacher/score-attempts/[0-9a-f-]+")))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.attemptNumber").value(1))
                .andExpect(jsonPath("$.scoreStorageType").value("INTEGER"))
                .andExpect(jsonPath("$.currentEffective").value(false));
    }

    @Test
    void requestCannotChooseSchoolId() throws Exception {
        expectRejectedCreateField("\"schoolId\":\"" + otherSchoolId + "\"");
    }

    @Test
    void requestCannotChooseEnteredBy() throws Exception {
        expectRejectedCreateField("\"enteredBy\":\"" + adminId + "\"");
    }

    @Test
    void requestCannotChooseAttemptNumber() throws Exception {
        expectRejectedCreateField("\"attemptNumber\":99");
    }

    @Test
    void requestCannotChooseStorageType() throws Exception {
        expectRejectedCreateField("\"scoreStorageType\":\"DECIMAL\"");
    }

    @Test
    void teacherCanListOwnEntries() throws Exception {
        UUID own = addAttempt(
                schoolId, activityProjectId, studentId, teacherId,
                1, "INTEGER", BigDecimal.valueOf(80), null, null, "DRAFT");
        addAttempt(
                schoolId, activityProjectId, studentId, adminId,
                2, "INTEGER", BigDecimal.valueOf(90), null, null, "DRAFT");

        mvc.perform(get("/api/v1/teacher/score-attempts/mine")
                        .with(auth(teacherId, schoolId, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].attemptId")
                        .value(own.toString()));
    }

    @Test
    void teacherCannotReadOtherTeacherEntry() throws Exception {
        UUID other = addAttempt(
                schoolId, activityProjectId, studentId, adminId,
                1, "INTEGER", BigDecimal.valueOf(80), null, null, "DRAFT");

        mvc.perform(get("/api/v1/teacher/score-attempts/" + other)
                        .with(auth(teacherId, schoolId, "TEACHER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void teacherCanReviseOwnRejectedEntry() throws Exception {
        UUID rejected = addAttempt(
                schoolId, activityProjectId, studentId, teacherId,
                1, "INTEGER", BigDecimal.valueOf(80), null, null, "REJECTED");
        jdbc.update("""
                INSERT INTO score_review_records(
                  id,score_attempt_id,reviewer_id,review_result,
                  review_comment,reject_reason,reviewed_at)
                VALUES (?,?,?,?,?,?,?)
                """, UUID.randomUUID(), rejected, adminId, "REJECTED",
                "check again", "wrong value", ts(Instant.now()));

        mvc.perform(patch("/api/v1/teacher/score-attempts/"
                        + rejected + "/draft")
                        .with(csrf())
                        .with(auth(teacherId, schoolId, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(integerUpdateBody(88)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.integerValue").value(88))
                .andExpect(jsonPath("$.reviewHistory[0].rejectReason")
                        .value("wrong value"));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM score_review_records
                WHERE score_attempt_id=?
                """, Integer.class, rejected)).isEqualTo(1);
    }

    @Test
    void teacherCanResubmitOwnDraft() throws Exception {
        UUID draft = addAttempt(
                schoolId, activityProjectId, studentId, teacherId,
                1, "INTEGER", BigDecimal.valueOf(80), null, null, "DRAFT");

        mvc.perform(post("/api/v1/teacher/score-attempts/"
                        + draft + "/submit")
                        .with(csrf())
                        .with(auth(teacherId, schoolId, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    }

    @Test
    void terminalActivityReturns409() throws Exception {
        jdbc.update(
                "UPDATE activities SET execution_status='ENDED' WHERE id=?",
                activityId);

        mvc.perform(post("/api/v1/teacher/score-attempts")
                        .with(csrf())
                        .with(auth(teacherId, schoolId, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(integerCreateBody()))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidValueCombinationReturns400() throws Exception {
        String body = integerCreateBody(
                "\"decimalValue\":12.5,");

        mvc.perform(post("/api/v1/teacher/score-attempts")
                        .with(csrf())
                        .with(auth(teacherId, schoolId, "TEACHER"))
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

        mvc.perform(post("/api/v1/teacher/score-attempts")
                        .with(csrf())
                        .with(auth(teacherId, schoolId, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationMs").value(0))
                .andExpect(jsonPath("$.scoreStorageType").value("DURATION"));
    }

    @Test
    void legacyClientCannotChooseAttemptNumber() throws Exception {
        mvc.perform(post("/api/v1/score-attempts")
                        .with(csrf())
                        .with(auth(teacherId, schoolId, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(legacyCreateBody(99, "INTEGER")))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("""
                SELECT attempt_number FROM score_attempts
                WHERE entered_by=? AND activity_project_id=?
                """, Integer.class, teacherId, activityProjectId)).isEqualTo(1);
    }

    @Test
    void legacyClientCannotOverrideStorageType() throws Exception {
        mvc.perform(post("/api/v1/score-attempts")
                        .with(csrf())
                        .with(auth(teacherId, schoolId, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(legacyCreateBody(1, "DECIMAL")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void legacyEndpointStillRequiresTeacher() throws Exception {
        mvc.perform(post("/api/v1/score-attempts")
                        .with(csrf())
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(legacyCreateBody(1, "INTEGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void legacyEndpointStillRequiresResponsibleAssignment() throws Exception {
        jdbc.update(
                "DELETE FROM responsible_teachers WHERE id=?",
                responsibleAssignmentId);

        mvc.perform(post("/api/v1/score-attempts")
                        .with(csrf())
                        .with(auth(teacherId, schoolId, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(legacyCreateBody(1, "INTEGER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void legacyEndpointUsesServerSchoolIdAndCurrentActor() throws Exception {
        mvc.perform(post("/api/v1/score-attempts")
                        .with(csrf())
                        .with(auth(teacherId, schoolId, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(legacyCreateBody(1, "INTEGER")))
                .andExpect(status().isCreated());

        var row = jdbc.queryForMap("""
                SELECT school_id, entered_by FROM score_attempts
                WHERE activity_project_id=? AND student_id=?
                """, activityProjectId, studentId);
        assertThat(row.get("school_id")).isEqualTo(schoolId);
        assertThat(row.get("entered_by")).isEqualTo(teacherId);
    }

    private void expectRejectedCreateField(String field) throws Exception {
        mvc.perform(post("/api/v1/teacher/score-attempts")
                        .with(csrf())
                        .with(auth(teacherId, schoolId, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(integerCreateBody(field + ",")))
                .andExpect(status().isBadRequest());
    }

    private String integerCreateBody() {
        return integerCreateBody("");
    }

    private String integerCreateBody(String extraField) {
        return """
                {
                  %s
                  "activityProjectId":"%s",
                  "studentId":"%s",
                  "integerValue":100,
                  "scoreBusinessTime":"2026-07-30T10:00:00Z",
                  "timeSource":"ON_SITE_RECORD"
                }
                """.formatted(extraField, activityProjectId, studentId);
    }

    private static String integerUpdateBody(long value) {
        return """
                {
                  "integerValue":%d,
                  "scoreBusinessTime":"2026-07-30T10:00:00Z",
                  "timeSource":"ON_SITE_RECORD"
                }
                """.formatted(value);
    }

    private String legacyCreateBody(int attemptNumber, String storageType) {
        return """
                {
                  "activityProjectId":"%s",
                  "studentId":"%s",
                  "attemptNumber":%d,
                  "scoreStorageType":"%s",
                  "integerValue":100,
                  "scoreBusinessTime":"2026-07-30T10:00:00Z",
                  "timeSource":"ON_SITE_RECORD"
                }
                """.formatted(
                activityProjectId, studentId, attemptNumber, storageType);
    }
}
