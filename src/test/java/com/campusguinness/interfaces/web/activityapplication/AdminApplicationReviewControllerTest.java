package com.campusguinness.interfaces.web.activityapplication;

import com.campusguinness.activity.application.query.port.AdminApplicationQueryPort;
import com.campusguinness.activity.application.service.ActivityApplicationService;
import com.campusguinness.infrastructure.security.CurrentActor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminApplicationReviewController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
class AdminApplicationReviewControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AdminApplicationQueryPort queryPort;
    @MockitoBean ActivityApplicationService service;
    @MockitoBean CurrentActor currentActor;

    UUID appId = UUID.randomUUID();

    @Test void getStatsReturns200() throws Exception {
        when(queryPort.getStats()).thenReturn(new AdminApplicationQueryPort.ApplicationStats(5,1,2,1,0,0,3));
        mvc.perform(get("/api/v1/admin/activity-applications/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdToday").value(3));
    }

    @Test void getSchoolsReturnsList() throws Exception {
        when(queryPort.getApplicationSchools()).thenReturn(List.of(
                new AdminApplicationQueryPort.SchoolOption(UUID.randomUUID(), "Test School")));
        mvc.perform(get("/api/v1/admin/activity-applications/schools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].schoolName").value("Test School"));
    }

    @Test void invalidStatusReturns400() throws Exception {
        mvc.perform(get("/api/v1/admin/activity-applications").param("status","INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test void invalidSortReturns400() throws Exception {
        mvc.perform(get("/api/v1/admin/activity-applications").param("sort","invalid_sort"))
                .andExpect(status().isBadRequest());
    }

    @Test void rejectWithBlankReasonReturns400() throws Exception {
        mvc.perform(post("/api/v1/admin/activity-applications/" + appId + "/reject")
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void rejectWithLongReasonReturns400() throws Exception {
        mvc.perform(post("/api/v1/admin/activity-applications/" + appId + "/reject")
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + "x".repeat(600) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void getDetailNotFoundReturns404() throws Exception {
        when(queryPort.findById(appId)).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/admin/activity-applications/" + appId))
                .andExpect(status().isNotFound());
    }

    @Test void listWithDates() throws Exception {
        when(queryPort.findApplications(any(),any(),any(),any(),any(),any(),eq(0),eq(20)))
                .thenReturn(new com.campusguinness.project.application.query.model.QueryPage<>(List.of(),0,20,0));
        mvc.perform(get("/api/v1/admin/activity-applications")
                .param("submittedFrom","2026-01-01T00:00:00Z")
                .param("submittedTo","2026-12-31T23:59:59Z")
                .param("page","0").param("size","20"))
                .andExpect(status().isOk());
    }

    @Test void listInvalidDateRangeReturns400() throws Exception {
        mvc.perform(get("/api/v1/admin/activity-applications")
                .param("submittedFrom","2026-12-31T00:00:00Z")
                .param("submittedTo","2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }
}
