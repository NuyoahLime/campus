package com.campusguinness.interfaces.web.activityresult;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.result.application.query.ActivityResultGovernanceQueryService;
import com.campusguinness.result.application.query.model.GovernanceActivityResultDetail;
import com.campusguinness.result.application.query.model.GovernanceActivityResultSummary;
import com.campusguinness.result.application.result.ActivityResultResult;
import com.campusguinness.result.application.service.ActivityResultGovernanceApplicationService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SuperAdminActivityResultGovernanceController.class)
@AutoConfigureMockMvc(addFilters = false)
class ActivityResultGovernanceControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private ActivityResultGovernanceApplicationService service;
    @MockitoBean private ActivityResultGovernanceQueryService queryService;

    @Test
    void governanceListDelegatesReadFilters() throws Exception {
        UUID resultId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        when(queryService.list(0, 20, "PUBLIC", false, "central"))
                .thenReturn(new QueryPage<>(List.of(new GovernanceActivityResultSummary(
                        resultId, schoolId, activityId, "Central School", "Finals", "ENDED",
                        "INTERNAL_PUBLISHED", "PUBLIC", false, UUID.randomUUID(), Instant.now())),
                        0, 20, 1));

        mvc.perform(get("/api/v1/super-admin/activity-results/governance")
                        .param("publicStatus", "PUBLIC")
                        .param("blocked", "false")
                        .param("q", "central"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].schoolName").value("Central School"));

        verify(queryService).list(0, 20, "PUBLIC", false, "central");
    }

    @Test
    void governanceDetailDelegatesExactResultId() throws Exception {
        UUID resultId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        when(queryService.detail(resultId)).thenReturn(new GovernanceActivityResultDetail(
                resultId, schoolId, activityId, "Central School", "Finals", "CANCELLED",
                "INTERNAL_PUBLISHED", "PLATFORM_TAKEDOWN", true, null, null, UUID.randomUUID(),
                null, List.of(), Instant.now()));

        mvc.perform(get("/api/v1/super-admin/activity-results/{id}/governance", resultId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultId").value(resultId.toString()))
                .andExpect(jsonPath("$.activityExecutionStatus").value("CANCELLED"));

        verify(queryService).detail(resultId);
    }

    @Test
    void takedownDelegatesResultAndReasonOnly() throws Exception {
        UUID resultId = UUID.randomUUID();
        when(service.takedown(resultId, "  emergency  ")).thenReturn(
                new ActivityResultResult(resultId, "INTERNAL_PUBLISHED", "PLATFORM_TAKEDOWN"));

        mvc.perform(post("/api/v1/super-admin/activity-results/{id}/takedown", resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  emergency  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(resultId.toString()))
                .andExpect(jsonPath("$.publicStatus").value("PLATFORM_TAKEDOWN"));

        verify(service).takedown(resultId, "  emergency  ");
    }

    @Test
    void resetTakedownDelegatesResultAndReasonOnly() throws Exception {
        UUID resultId = UUID.randomUUID();
        when(service.resetTakedown(resultId, "  reviewed  ")).thenReturn(
                new ActivityResultResult(resultId, "INTERNAL_PUBLISHED", "NOT_SUBMITTED"));

        mvc.perform(post("/api/v1/super-admin/activity-results/{id}/reset-takedown", resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  reviewed  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(resultId.toString()))
                .andExpect(jsonPath("$.publicStatus").value("NOT_SUBMITTED"));

        verify(service).resetTakedown(resultId, "  reviewed  ");
    }

    @Test
    void resetTakedownDtoAllowsPaddedReasonForNormalizedApplicationValidation() throws Exception {
        UUID resultId = UUID.randomUUID();
        String padded = "  " + "x".repeat(2000) + "  ";
        when(service.resetTakedown(resultId, padded)).thenReturn(
                new ActivityResultResult(resultId, "INTERNAL_PUBLISHED", "NOT_SUBMITTED"));

        mvc.perform(post("/api/v1/super-admin/activity-results/{id}/reset-takedown", resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + padded + "\"}"))
                .andExpect(status().isOk());

        verify(service).resetTakedown(resultId, padded);
    }

    @Test
    void nullBlankAndOversizedReasonsReturnControlledValidationErrors() throws Exception {
        UUID resultId = UUID.randomUUID();
        String path = "/api/v1/super-admin/activity-results/{id}/takedown";

        mvc.perform(post(path, resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post(path, resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post(path, resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + "x".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void resetTakedownNullAndBlankReasonsReturnControlledValidationErrors() throws Exception {
        UUID resultId = UUID.randomUUID();
        String path = "/api/v1/super-admin/activity-results/{id}/reset-takedown";

        mvc.perform(post(path, resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post(path, resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
