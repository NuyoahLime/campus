package com.campusguinness.interfaces.web.activityapplication;

import com.campusguinness.activity.application.query.port.TeacherApplicationQueryPort;
import com.campusguinness.activity.application.result.ActivityApplicationResult;
import com.campusguinness.activity.application.service.ActivityApplicationService;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
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
    @MockitoBean TeacherApplicationQueryPort queryPort;
    @MockitoBean CurrentActor currentActor;
    @Autowired ObjectMapper mapper;

    UUID appId = UUID.randomUUID();
    UUID schoolId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    private ActivityApplicationResult fullResult() {
        return new ActivityApplicationResult(appId, schoolId, "Test School", "title", "desc", "SUBMITTED", null, null, null, null, 1, null, null);
    }

    @BeforeEach void setUp() {
        when(currentActor.requireUserId()).thenReturn(userId);
        when(queryPort.findMineById(eq(userId), any())).thenReturn(Optional.of(fullResult()));
    }

    @Test void submitReturns201() throws Exception {
        when(service.submit(any(), eq(userId))).thenReturn(fullResult());
        mvc.perform(post("/api/v1/activity-applications").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new SubmitActivityApplicationRequest(schoolId, "title", "desc"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.schoolName").value("Test School"));
    }

    @Test void getMineReturns200() throws Exception {
        mvc.perform(get("/api/v1/activity-applications/mine/" + appId)).andExpect(status().isOk()).andExpect(jsonPath("$.schoolName").value("Test School"));
    }

    @Test void getMineReturns404() throws Exception {
        when(queryPort.findMineById(eq(userId), any())).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/activity-applications/mine/" + appId)).andExpect(status().isNotFound());
    }

    @Test void withdrawReturns200() throws Exception {
        mvc.perform(post("/api/v1/activity-applications/mine/" + appId + "/withdraw"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.schoolName").value("Test School"));
    }

    @Test void returnToDraftReturns200() throws Exception {
        mvc.perform(post("/api/v1/activity-applications/mine/" + appId + "/return-to-draft"))
                .andExpect(status().isOk());
    }

    @Test void resubmitReturns200() throws Exception {
        mvc.perform(post("/api/v1/activity-applications/mine/" + appId + "/submit"))
                .andExpect(status().isOk());
    }

    @Test void updateDraftReturns200() throws Exception {
        mvc.perform(put("/api/v1/activity-applications/mine/" + appId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"updated\",\"description\":\"\"}"))
                .andExpect(status().isOk());
    }
}
