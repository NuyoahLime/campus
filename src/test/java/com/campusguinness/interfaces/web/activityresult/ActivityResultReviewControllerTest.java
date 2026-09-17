package com.campusguinness.interfaces.web.activityresult;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.result.application.query.ActivityResultReviewQueryService;
import com.campusguinness.result.application.query.model.PendingResultReviewDetail;
import com.campusguinness.result.application.query.model.PendingResultReviewSummary;
import com.campusguinness.result.application.query.model.ResultReviewHistoryEntry;
import com.campusguinness.result.application.result.ActivityResultResult;
import com.campusguinness.result.application.service.ActivityResultReviewApplicationService;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ActivityResultReviewController.class, SuperAdminActivityResultReviewController.class})
@AutoConfigureMockMvc(addFilters = false)
class ActivityResultReviewControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private ActivityResultReviewApplicationService service;
    @MockitoBean private ActivityResultReviewQueryService queryService;

    @Test
    void submitDelegatesCandidateBoundCommand() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.submit(id)).thenReturn(
                new ActivityResultResult(id, "INTERNAL_PUBLISHED", "PENDING_PUBLIC_REVIEW"));

        mvc.perform(post("/api/v1/activity-results/{id}/submit-public-review", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.publicStatus").value("PENDING_PUBLIC_REVIEW"));
    }

    @Test
    void pendingListAndDetailReturnReviewOnlyModels() throws Exception {
        UUID resultId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID submitterId = UUID.randomUUID();
        Instant submittedAt = Instant.parse("2026-09-17T08:00:00Z");
        var summary = new PendingResultReviewSummary(
                resultId, schoolId, activityId, versionId, 2, "Candidate",
                submittedAt, submitterId, "PENDING_PUBLIC_REVIEW");
        var detail = new PendingResultReviewDetail(
                resultId, schoolId, activityId, versionId, 2, "Candidate", "Summary",
                List.of("100 points"), List.of(), submittedAt, submitterId,
                "PENDING_PUBLIC_REVIEW",
                List.of(new ResultReviewHistoryEntry(
                        UUID.randomUUID(), versionId, "SUBMITTED", submitterId,
                        submittedAt, null, null, null, submittedAt)));
        when(queryService.listPending(0, 20)).thenReturn(new QueryPage<>(List.of(summary), 0, 20, 1));
        when(queryService.pendingDetail(resultId)).thenReturn(detail);

        mvc.perform(get("/api/v1/super-admin/activity-results/public-reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].candidateVersionId").value(versionId.toString()));
        mvc.perform(get("/api/v1/super-admin/activity-results/{id}/public-review", resultId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateTitle").value("Candidate"))
                .andExpect(jsonPath("$.history[0].action").value("SUBMITTED"));
    }

    @Test
    void approveAndRejectDelegateReviewCommands() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.approve(id)).thenReturn(
                new ActivityResultResult(id, "INTERNAL_PUBLISHED", "PLATFORM_APPROVED"));
        when(service.reject(id, "needs evidence")).thenReturn(
                new ActivityResultResult(id, "INTERNAL_PUBLISHED", "PLATFORM_REJECTED"));

        mvc.perform(post("/api/v1/super-admin/activity-results/{id}/approve-public-review", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicStatus").value("PLATFORM_APPROVED"));
        mvc.perform(post("/api/v1/super-admin/activity-results/{id}/reject-public-review", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"needs evidence\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicStatus").value("PLATFORM_REJECTED"));
        verify(service).reject(id, "needs evidence");
    }

    @Test
    void blankOrOversizedRejectionReasonReturnsControlledBadRequest() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(post("/api/v1/super-admin/activity-results/{id}/reject-public-review", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/v1/super-admin/activity-results/{id}/reject-public-review", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + "x".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
