package com.campusguinness.interfaces.web.studentidentityapplication;

import com.campusguinness.identity.application.query.ReviewPageResult;
import com.campusguinness.identity.application.query.StudentIdentityApplicationDetail;
import com.campusguinness.identity.application.query.StudentIdentityApplicationSummary;
import com.campusguinness.identity.application.result.StudentIdentityApplicationReviewResult;
import com.campusguinness.identity.application.service.ApproveStudentIdentityApplicationService;
import com.campusguinness.identity.application.service.RejectStudentIdentityApplicationService;
import com.campusguinness.identity.application.service.StudentIdentityApplicationReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentIdentityApplicationReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
class StudentIdentityApplicationReviewControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean StudentIdentityApplicationReviewService reviewService;
    @MockitoBean ApproveStudentIdentityApplicationService approveService;
    @MockitoBean RejectStudentIdentityApplicationService rejectService;

    @Test
    void listReturnsPageResponse() throws Exception {
        UUID schoolId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(reviewService.list(eq(schoolId), eq("PENDING"), eq(0), eq(20)))
                .thenReturn(new ReviewPageResult<>(
                        List.of(new StudentIdentityApplicationSummary(
                                applicationId, userId, schoolId, "student01", "Student",
                                "SN-001", "Grade 10", "Class 1", "PENDING",
                                Instant.parse("2026-08-06T00:00:00Z"), null)),
                        0, 20, 1));

        mvc.perform(get("/api/v1/schools/" + schoolId + "/student-identity-applications"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].applicationId").value(applicationId.toString()))
                .andExpect(jsonPath("$.items[0].username").value("student01"));
    }

    @Test
    void detailReturnsReviewPayload() throws Exception {
        UUID schoolId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(reviewService.detail(eq(schoolId), eq(applicationId)))
                .thenReturn(new StudentIdentityApplicationDetail(
                        applicationId, userId, schoolId, "student01", "Student",
                        "SN-001", "Grade 10", "Class 1", "PENDING",
                        Instant.parse("2026-08-06T00:00:00Z"), null, null, null, List.of("proof/key")));

        mvc.perform(get("/api/v1/schools/" + schoolId + "/student-identity-applications/" + applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value(applicationId.toString()))
                .andExpect(jsonPath("$.proofFileCount").value(1))
                .andExpect(jsonPath("$.proofFileKeys[0]").value("proof/key"));
    }

    @Test
    void approveReturnsReviewResponse() throws Exception {
        UUID schoolId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(approveService.approve(eq(schoolId), eq(applicationId)))
                .thenReturn(new StudentIdentityApplicationReviewResult(
                        applicationId, userId, schoolId, "APPROVED", "NORMAL",
                        "STUDENT", "ACTIVE", null, Instant.parse("2026-08-06T00:00:00Z")));

        mvc.perform(post("/api/v1/schools/" + schoolId + "/student-identity-applications/" + applicationId + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.membershipRole").value("STUDENT"))
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    @Test
    void rejectValidatesReasonAndReturnsReviewResponse() throws Exception {
        UUID schoolId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(rejectService.reject(eq(schoolId), eq(applicationId), eq(" student number mismatch ")))
                .thenReturn(new StudentIdentityApplicationReviewResult(
                        applicationId, userId, schoolId, "REJECTED", "PENDING_ACTIVATION",
                        null, null, "student number mismatch", Instant.parse("2026-08-06T00:00:00Z")));

        mvc.perform(post("/api/v1/schools/" + schoolId + "/student-identity-applications/" + applicationId + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\" student number mismatch \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value("student number mismatch"));
    }

    @Test
    void rejectRequiresReason() throws Exception {
        UUID schoolId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        mvc.perform(post("/api/v1/schools/" + schoolId + "/student-identity-applications/" + applicationId + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }
}
