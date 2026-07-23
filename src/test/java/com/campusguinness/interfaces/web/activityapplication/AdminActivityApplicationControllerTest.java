package com.campusguinness.interfaces.web.activityapplication;

import com.campusguinness.activity.application.query.model.QueryPage;
import com.campusguinness.activity.application.query.port.ActivityApplicationQueryPort;
import com.campusguinness.activity.application.result.ActivityApplicationResult;
import com.campusguinness.activity.application.service.ActivityApplicationService;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminActivityApplicationController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
class AdminActivityApplicationControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ActivityApplicationService service;
    @MockitoBean ActivityApplicationQueryPort queryPort;
    @MockitoBean CurrentActor currentActor;
    @Autowired ObjectMapper mapper;

    UUID reviewerId = UUID.randomUUID();
    UUID appId = UUID.randomUUID();
    UUID schoolId = UUID.randomUUID();

    @Test void listReturns200() throws Exception {
        when(queryPort.findAll(null, null, 0, 20))
                .thenReturn(new QueryPage<>(Collections.emptyList(), 0, 20, 0));

        mvc.perform(get("/api/v1/admin/activity-applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void listWithStatusFilter() throws Exception {
        when(queryPort.findAll("SUBMITTED", null, 0, 20))
                .thenReturn(new QueryPage<>(Collections.emptyList(), 0, 20, 0));

        mvc.perform(get("/api/v1/admin/activity-applications?status=SUBMITTED"))
                .andExpect(status().isOk());
    }

    @Test void listWithSchoolFilter() throws Exception {
        when(queryPort.findAll(null, schoolId, 0, 20))
                .thenReturn(new QueryPage<>(Collections.emptyList(), 0, 20, 0));

        mvc.perform(get("/api/v1/admin/activity-applications?schoolId=" + schoolId))
                .andExpect(status().isOk());
    }

    @Test void listRejectsNegativePage() throws Exception {
        mvc.perform(get("/api/v1/admin/activity-applications?page=-1"))
                .andExpect(status().isBadRequest());
    }

    @Test void listRejectsExcessiveSize() throws Exception {
        mvc.perform(get("/api/v1/admin/activity-applications?size=101"))
                .andExpect(status().isBadRequest());
    }

    @Test void getByIdReturns200() throws Exception {
        var result = new ActivityApplicationResult(appId, schoolId, "title", "desc",
                "SUBMITTED", null, null, null, null, 1);
        when(queryPort.findById(appId)).thenReturn(Optional.of(result));

        mvc.perform(get("/api/v1/admin/activity-applications/" + appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value(appId.toString()));
    }

    @Test void getByIdReturns404() throws Exception {
        when(queryPort.findById(appId)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/admin/activity-applications/" + appId))
                .andExpect(status().isNotFound());
    }

    @Test void approveReturns200() throws Exception {
        when(currentActor.requireUserId()).thenReturn(reviewerId);
        when(service.approve(appId, reviewerId))
                .thenReturn(new ActivityApplicationResult(appId, schoolId, "t", "d", "APPROVED",
                        UUID.randomUUID(), null, null, null, 1));

        mvc.perform(post("/api/v1/admin/activity-applications/" + appId + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.createdActivityId").exists());
    }

    @Test void approveDoesNotAcceptReviewerIdOrActivityId() throws Exception {
        // Send body with reviewerId and activityId — they should be ignored
        when(currentActor.requireUserId()).thenReturn(reviewerId);
        when(service.approve(appId, reviewerId))
                .thenReturn(new ActivityApplicationResult(appId, schoolId, "t", "d", "APPROVED",
                        UUID.randomUUID(), null, null, null, 1));

        mvc.perform(post("/api/v1/admin/activity-applications/" + appId + "/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewerId\":\"" + UUID.randomUUID() + "\",\"activityId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());
    }

    @Test void approveDuplicateReturns409() throws Exception {
        when(currentActor.requireUserId()).thenReturn(reviewerId);
        when(service.approve(appId, reviewerId))
                .thenThrow(new IllegalStateException("Application already has a created Activity"));

        mvc.perform(post("/api/v1/admin/activity-applications/" + appId + "/approve"))
                .andExpect(status().isConflict());
    }

    @Test void rejectReturns200() throws Exception {
        when(currentActor.requireUserId()).thenReturn(reviewerId);
        when(service.reject(appId, reviewerId, "需要修改"))
                .thenReturn(new ActivityApplicationResult(appId, schoolId, "t", "d", "REJECTED",
                        null, null, null, "需要修改", 1));

        mvc.perform(post("/api/v1/admin/activity-applications/" + appId + "/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RejectActivityApplicationRequest("需要修改"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("需要修改"));
    }

    @Test void rejectDoesNotAcceptReviewerId() throws Exception {
        when(currentActor.requireUserId()).thenReturn(reviewerId);
        when(service.reject(appId, reviewerId, "reason"))
                .thenReturn(new ActivityApplicationResult(appId, schoolId, "t", "d", "REJECTED",
                        null, null, null, "reason", 1));

        mvc.perform(post("/api/v1/admin/activity-applications/" + appId + "/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewerId\":\"" + UUID.randomUUID() + "\",\"reason\":\"reason\"}"))
                .andExpect(status().isOk());
    }

    @Test void rejectReturns400WhenReasonBlank() throws Exception {
        mvc.perform(post("/api/v1/admin/activity-applications/" + appId + "/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RejectActivityApplicationRequest(""))))
                .andExpect(status().isBadRequest());
    }
}
