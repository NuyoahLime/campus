package com.campusguinness.interfaces.web.activityapplication;

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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ActivityApplicationController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
class ActivityApplicationControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ActivityApplicationService service;
    @MockitoBean CurrentActor currentActor;
    @Autowired ObjectMapper mapper;

    UUID userId = UUID.randomUUID();
    UUID schoolId = UUID.randomUUID();
    UUID appId = UUID.randomUUID();

    @Test void submitReturns201() throws Exception {
        when(currentActor.requireUserId()).thenReturn(userId);
        when(service.submit(any(), eq(userId)))
                .thenReturn(new ActivityApplicationResult(appId, schoolId, null, "t", "d", "SUBMITTED", null, null, null, null, 1, null, null));

        mvc.perform(post("/api/v1/activity-applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new SubmitActivityApplicationRequest(schoolId, "title", "desc"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationId").value(appId.toString()))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test void submitReturns400WhenTitleBlank() throws Exception {
        mvc.perform(post("/api/v1/activity-applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new SubmitActivityApplicationRequest(schoolId, "", "desc"))))
                .andExpect(status().isBadRequest());
    }

    @Test void listMineReturns200() throws Exception {
        when(currentActor.requireUserId()).thenReturn(userId);
        when(service.listMine(userId)).thenReturn(Collections.emptyList());

        mvc.perform(get("/api/v1/activity-applications/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test void getMineReturns200() throws Exception {
        when(currentActor.requireUserId()).thenReturn(userId);
        when(service.getMine(appId, userId))
                .thenReturn(new ActivityApplicationResult(appId, schoolId, null, "t", "d", "SUBMITTED", null, null, null, null, 1, null, null));

        mvc.perform(get("/api/v1/activity-applications/mine/" + appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value(appId.toString()))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test void getMineReturns404ForOtherUser() throws Exception {
        when(currentActor.requireUserId()).thenReturn(userId);
        when(service.getMine(appId, userId))
                .thenThrow(new IllegalArgumentException("ActivityApplication not found: " + appId));

        mvc.perform(get("/api/v1/activity-applications/mine/" + appId))
                .andExpect(status().isNotFound());
    }

    @Test void withdrawReturns200() throws Exception {
        when(currentActor.requireUserId()).thenReturn(userId);
        when(service.withdraw(appId, userId))
                .thenReturn(new ActivityApplicationResult(appId, schoolId, null, "t", "d", "WITHDRAWN", null, null, null, null, 1, null, null));

        mvc.perform(post("/api/v1/activity-applications/mine/" + appId + "/withdraw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }

    @Test void withdrawReturns404() throws Exception {
        when(currentActor.requireUserId()).thenReturn(userId);
        when(service.withdraw(appId, userId))
                .thenThrow(new IllegalArgumentException("ActivityApplication not found: " + appId));

        mvc.perform(post("/api/v1/activity-applications/mine/" + appId + "/withdraw"))
                .andExpect(status().isNotFound());
    }

    @Test void returnToDraftReturns200() throws Exception {
        when(currentActor.requireUserId()).thenReturn(userId);
        when(service.returnToDraft(appId, userId))
                .thenReturn(new ActivityApplicationResult(appId, schoolId, null, "t", "d", "DRAFT", null, null, null, null, 2, null, null));

        mvc.perform(post("/api/v1/activity-applications/mine/" + appId + "/return-to-draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationVersion").value(2));
    }

    @Test void resubmitReturns200() throws Exception {
        when(currentActor.requireUserId()).thenReturn(userId);
        when(service.resubmit(appId, userId))
                .thenReturn(new ActivityApplicationResult(appId, schoolId, null, "t", "d", "SUBMITTED", null, null, null, null, 1, null, null));

        mvc.perform(post("/api/v1/activity-applications/mine/" + appId + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }
}
