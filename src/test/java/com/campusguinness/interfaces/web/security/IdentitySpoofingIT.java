package com.campusguinness.interfaces.web.security;

import com.campusguinness.appeal.application.result.ScoreAppealResult;
import com.campusguinness.appeal.application.service.ScoreAppealApplicationService;
import com.campusguinness.feedback.application.result.FeedbackResult;
import com.campusguinness.feedback.application.service.FeedbackApplicationService;
import com.campusguinness.interfaces.web.feedback.FeedbackController;
import com.campusguinness.interfaces.web.schoolregistration.SchoolRegistrationController;
import com.campusguinness.interfaces.web.scoreappeal.ScoreAppealController;
import com.campusguinness.school.application.result.SchoolRegistrationResult;
import com.campusguinness.school.application.query.SchoolRegistrationQueryService;
import com.campusguinness.school.application.service.SchoolRegistrationApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ScoreAppealController.class, SchoolRegistrationController.class, FeedbackController.class})
@AutoConfigureMockMvc(addFilters = false)
class IdentitySpoofingIT {

    @Autowired MockMvc mvc;
    @MockitoBean ScoreAppealApplicationService scoreAppeals;
    @MockitoBean SchoolRegistrationApplicationService schoolRegistrations;
    @MockitoBean SchoolRegistrationQueryService schoolRegistrationQueries;
    @MockitoBean FeedbackApplicationService feedbacks;

    @Test
    void studentCannotSubmitScoreAppealAsAnotherStudentThroughRequestStudentId() throws Exception {
        UUID schoolId = UUID.randomUUID();
        UUID scoreAttemptId = UUID.randomUUID();
        UUID spoofedStudentId = UUID.randomUUID();
        UUID appealId = UUID.randomUUID();
        when(scoreAppeals.submit(eq(schoolId), eq(scoreAttemptId), eq("SCORE"), eq("reason")))
                .thenReturn(new ScoreAppealResult(appealId, "SUBMITTED"));

        mvc.perform(post("/api/v1/score-appeals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schoolId": "%s",
                                  "scoreAttemptId": "%s",
                                  "studentId": "%s",
                                  "appealType": "SCORE",
                                  "appealReason": "reason"
                                }
                                """.formatted(schoolId, scoreAttemptId, spoofedStudentId)))
                .andExpect(status().isCreated());

        verify(scoreAppeals).submit(schoolId, scoreAttemptId, "SCORE", "reason");
    }

    @Test
    void schoolAdminCannotApproveSchoolRegistrationAsAnotherReviewerThroughRequestReviewerId() throws Exception {
        UUID registrationId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID spoofedReviewerId = UUID.randomUUID();
        when(schoolRegistrations.approve(eq(registrationId), eq("ok"), eq(schoolId)))
                .thenReturn(new SchoolRegistrationResult(registrationId, "school", "APPROVED", schoolId));

        mvc.perform(post("/api/v1/school-registrations/{id}/approve", registrationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerId": "%s",
                                  "comment": "ok",
                                  "schoolId": "%s"
                                }
                                """.formatted(spoofedReviewerId, schoolId)))
                .andExpect(status().isOk());

        verify(schoolRegistrations).approve(registrationId, "ok", schoolId);
    }

    @Test
    void studentCannotSubmitFeedbackAsSuperAdminThroughRequestSubmitterId() throws Exception {
        UUID schoolId = UUID.randomUUID();
        UUID spoofedSuperAdminId = UUID.randomUUID();
        UUID feedbackId = UUID.randomUUID();
        when(feedbacks.submit(eq(schoolId), eq("GENERAL"), eq("content")))
                .thenReturn(new FeedbackResult(feedbackId, "SUBMITTED"));

        mvc.perform(post("/api/v1/feedbacks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schoolId": "%s",
                                  "submitterId": "%s",
                                  "feedbackType": "GENERAL",
                                  "content": "content"
                                }
                                """.formatted(schoolId, spoofedSuperAdminId)))
                .andExpect(status().isCreated());

        verify(feedbacks).submit(schoolId, "GENERAL", "content");
    }
}
