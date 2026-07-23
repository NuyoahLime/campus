package com.campusguinness.interfaces.web.activity;

import com.campusguinness.activity.application.query.ActivityQueryService;
import com.campusguinness.activity.application.result.ActivityResult;
import com.campusguinness.activity.application.service.ActivityManagementService;
import com.campusguinness.activity.internal.domain.*;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminActivityReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminActivityReviewControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ActivityManagementService service;
    @MockitoBean ActivityQueryService queryService;
    @MockitoBean CurrentActor currentActor;
    @Autowired ObjectMapper mapper;

    UUID activityId = UUID.randomUUID();
    UUID schoolId = UUID.randomUUID();

    @Test void listReviewReturns200() throws Exception {
        when(queryService.listPublicReview(null, null, 0, 20))
                .thenReturn(new com.campusguinness.project.application.query.model.QueryPage<>(
                        Collections.emptyList(), 0, 20, 0));
        mvc.perform(get("/api/v1/admin/activities/public-review"))
                .andExpect(status().isOk());
    }

    @Test void listReviewWithFilters() throws Exception {
        when(queryService.listPublicReview(eq(schoolId.toString()), eq("PENDING_PLATFORM_REVIEW"), eq(0), eq(20)))
                .thenReturn(new com.campusguinness.project.application.query.model.QueryPage<>(
                        Collections.emptyList(), 0, 20, 0));
        mvc.perform(get("/api/v1/admin/activities/public-review?schoolId=" + schoolId + "&publicStatus=PENDING_PLATFORM_REVIEW"))
                .andExpect(status().isOk());
    }

    @Test void approveReviewReturns200() throws Exception {
        when(service.platformApprove(activityId))
                .thenReturn(new ActivityResult(activityId, "PUBLISHED", "PLATFORM_APPROVED"));
        mvc.perform(post("/api/v1/admin/activities/" + activityId + "/approve-public-review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicStatus").value("PLATFORM_APPROVED"));
    }

    @Test void rejectReviewReturns200() throws Exception {
        when(service.platformReject(eq(activityId), eq("need changes")))
                .thenReturn(new ActivityResult(activityId, "PUBLISHED", "PLATFORM_REJECTED"));
        mvc.perform(post("/api/v1/admin/activities/" + activityId + "/reject-public-review")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"need changes\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicStatus").value("PLATFORM_REJECTED"));
    }

    @Test void makePublicReturns200() throws Exception {
        when(service.makePublic(activityId))
                .thenReturn(new ActivityResult(activityId, "PUBLISHED", "PUBLIC"));
        mvc.perform(post("/api/v1/admin/activities/" + activityId + "/make-public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicStatus").value("PUBLIC"));
    }

    @Test void takeDownReturns200() throws Exception {
        when(service.platformTakedown(eq(activityId), eq("inappropriate")))
                .thenReturn(new ActivityResult(activityId, "PUBLISHED", "PLATFORM_TAKEDOWN"));
        mvc.perform(post("/api/v1/admin/activities/" + activityId + "/take-down")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"inappropriate\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicStatus").value("PLATFORM_TAKEDOWN"));
    }

    @Test void rejectRequiresReason() throws Exception {
        mvc.perform(post("/api/v1/admin/activities/" + activityId + "/reject-public-review")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void detailReturnsActivity() throws Exception {
        var act = Activity.create(new Activity.Builder()
                .id(new ActivityId(activityId)).schoolId(schoolId)
                .createdBy(UUID.randomUUID()).title("Test"));
        when(service.findById(activityId)).thenReturn(act);
        when(service.listProjects(activityId)).thenReturn(Collections.emptyList());
        mvc.perform(get("/api/v1/admin/activities/public-review/" + activityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityId").value(activityId.toString()));
    }
}
