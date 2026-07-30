package com.campusguinness.interfaces.web.scoreattempt;

import com.campusguinness.score.ScoreReviewIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class SchoolAdminScoreReviewControllerIT extends ScoreReviewIntegrationTestSupport {
    @Autowired MockMvc mvc;

    @Test
    void unauthenticatedListReturns401() throws Exception {
        mvc.perform(get("/api/v1/school-admin/score-attempts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void teacherCannotUseSchoolAdminReviewApi() throws Exception {
        mvc.perform(get("/api/v1/school-admin/score-attempts")
                        .with(auth(teacherId, schoolId, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotUseSchoolAdminReviewApi() throws Exception {
        mvc.perform(get("/api/v1/school-admin/score-attempts")
                        .with(auth(studentId, schoolId, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void schoolAdminCanListOwnSchoolAttempts() throws Exception {
        mvc.perform(get("/api/v1/school-admin/score-attempts")
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].attemptId").value(attemptId.toString()));
    }

    @Test
    void schoolAdminCannotReadOtherSchoolAttempt() throws Exception {
        UUID otherAttempt = insertAttempt(
                otherSchoolId, entrantId, "PENDING_REVIEW", false, 101);
        mvc.perform(get("/api/v1/school-admin/score-attempts/" + otherAttempt)
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void schoolAdminCanApprovePendingAttempt() throws Exception {
        approve(attemptId, adminId).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.currentEffective").value(true));
    }

    @Test
    void schoolAdminCanRejectPendingAttempt() throws Exception {
        reject(attemptId, adminId, "evidence mismatch").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.currentEffective").value(false));
    }

    @Test
    void entrantCannotApproveOwnAttempt() throws Exception {
        approve(attemptId, entrantId).andExpect(status().isForbidden());
    }

    @Test
    void entrantCannotRejectOwnAttempt() throws Exception {
        reject(attemptId, entrantId, "invalid").andExpect(status().isForbidden());
    }

    @Test
    void invalidPageReturns400() throws Exception {
        mvc.perform(get("/api/v1/school-admin/score-attempts?page=-1")
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidSizeReturns400() throws Exception {
        mvc.perform(get("/api/v1/school-admin/score-attempts?size=101")
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overlongKeywordReturns400() throws Exception {
        mvc.perform(get("/api/v1/school-admin/score-attempts?keyword=" + "x".repeat(101))
                        .with(auth(adminId, schoolId, "SCHOOL_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankRejectReasonReturns400() throws Exception {
        reject(attemptId, adminId, "   ").andExpect(status().isBadRequest());
    }

    @Test
    void duplicateReviewReturns409() throws Exception {
        approve(attemptId, adminId).andExpect(status().isOk());
        approve(attemptId, adminId).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM score_review_records WHERE score_attempt_id=?
                """, Integer.class, attemptId)).isEqualTo(1);
    }

    @Test
    void approveWritesReviewHistory() throws Exception {
        approve(attemptId, adminId).andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewHistory[0].reviewResult").value("APPROVED"))
                .andExpect(jsonPath("$.reviewHistory[0].reviewComment").value("verified"));
    }

    @Test
    void rejectWritesRejectReason() throws Exception {
        reject(attemptId, adminId, "evidence mismatch").andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewHistory[0].reviewResult").value("REJECTED"))
                .andExpect(jsonPath("$.reviewHistory[0].rejectReason").value("evidence mismatch"));
    }

    private org.springframework.test.web.servlet.ResultActions approve(
            UUID id, UUID reviewer) throws Exception {
        return mvc.perform(post("/api/v1/school-admin/score-attempts/" + id + "/approve")
                .with(csrf())
                .with(auth(reviewer, schoolId, "SCHOOL_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewComment\":\"verified\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions reject(
            UUID id, UUID reviewer, String reason) throws Exception {
        return mvc.perform(post("/api/v1/school-admin/score-attempts/" + id + "/reject")
                .with(csrf())
                .with(auth(reviewer, schoolId, "SCHOOL_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rejectReason\":\"" + reason + "\",\"reviewComment\":\"retry\"}"));
    }
}
