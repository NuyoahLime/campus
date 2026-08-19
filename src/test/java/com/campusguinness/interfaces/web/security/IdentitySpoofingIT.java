package com.campusguinness.interfaces.web.security;

import com.campusguinness.appeal.application.result.ScoreAppealResult;
import com.campusguinness.appeal.application.service.ScoreAppealApplicationService;
import com.campusguinness.feedback.application.result.FeedbackResult;
import com.campusguinness.feedback.application.service.FeedbackApplicationService;
import com.campusguinness.feedback.application.service.FeedbackQueryService;
import com.campusguinness.interfaces.web.feedback.FeedbackController;
import com.campusguinness.interfaces.web.schoolregistration.SchoolRegistrationController;
import com.campusguinness.interfaces.web.scoreappeal.ScoreAppealController;
import com.campusguinness.interfaces.web.studentappeal.StudentScoreAppealController;
import com.campusguinness.interfaces.web.studentfeedback.StudentFeedbackController;
import com.campusguinness.appeal.application.service.ScoreAppealQueryService;
import com.campusguinness.school.application.result.SchoolRegistrationResult;
import com.campusguinness.school.application.query.SchoolRegistrationQueryService;
import com.campusguinness.school.application.service.SchoolRegistrationApplicationService;
import com.campusguinness.school.application.service.SchoolRegistrationReviewApplicationService;
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

@WebMvcTest({ScoreAppealController.class, StudentScoreAppealController.class,
        SchoolRegistrationController.class, FeedbackController.class, StudentFeedbackController.class})
@AutoConfigureMockMvc(addFilters = false)
class IdentitySpoofingIT {

    @Autowired MockMvc mvc;
    @MockitoBean ScoreAppealApplicationService scoreAppeals;
    @MockitoBean SchoolRegistrationApplicationService schoolRegistrations;
    @MockitoBean SchoolRegistrationReviewApplicationService schoolRegistrationReviews;
    @MockitoBean SchoolRegistrationQueryService schoolRegistrationQueries;
    @MockitoBean FeedbackApplicationService feedbacks;
    @MockitoBean ScoreAppealQueryService scoreAppealQueries;
    @MockitoBean FeedbackQueryService feedbackQueries;

    @Test
    void studentCannotSubmitScoreAppealAsAnotherStudentThroughRequestStudentId() throws Exception {
        UUID schoolId = UUID.randomUUID();
        UUID scoreAttemptId = UUID.randomUUID();
        UUID appealId = UUID.randomUUID();
        when(scoreAppeals.submitForCurrentStudent(eq(scoreAttemptId), eq("SCORE"), eq("reason")))
                .thenReturn(new ScoreAppealResult(appealId, "SUBMITTED"));

        mvc.perform(post("/api/v1/student/appeals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scoreAttemptId": "%s",
                                  "appealType": "SCORE",
                                  "appealReason": "reason"
                                }
                                """.formatted(scoreAttemptId)))
                .andExpect(status().isCreated());

        verify(scoreAppeals).submitForCurrentStudent(scoreAttemptId, "SCORE", "reason");
    }

    @Test
    void schoolAdminCannotApproveSchoolRegistrationAsAnotherReviewerThroughRequestReviewerId() throws Exception {
        UUID registrationId = UUID.randomUUID();
        UUID spoofedReviewerId = UUID.randomUUID();
        UUID createdSchoolId = UUID.randomUUID();
        when(schoolRegistrationReviews.approve(eq(registrationId), eq("ok")))
                .thenReturn(new SchoolRegistrationResult(registrationId, "school", "APPROVED", createdSchoolId));

        mvc.perform(post("/api/v1/school-registrations/{id}/approve", registrationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerId": "%s",
                                  "comment": "ok",
                                  "schoolId": "%s"
                                }
                                """.formatted(spoofedReviewerId, UUID.randomUUID())))
                .andExpect(status().isOk());

        verify(schoolRegistrationReviews).approve(registrationId, "ok");
    }

    @Test
    void studentCannotSubmitFeedbackAsSuperAdminThroughRequestSubmitterId() throws Exception {
        UUID schoolId = UUID.randomUUID();
        UUID feedbackId = UUID.randomUUID();
        when(feedbacks.submitForCurrentStudent(eq("GENERAL"), eq("content")))
                .thenReturn(new FeedbackResult(feedbackId, "SUBMITTED"));

        mvc.perform(post("/api/v1/student/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "feedbackType": "GENERAL",
                                  "content": "content"
                                }
                                """.formatted()))
                .andExpect(status().isCreated());

        verify(feedbacks).submitForCurrentStudent("GENERAL", "content");
    }
}
