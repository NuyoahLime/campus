package com.campusguinness.interfaces.web.activity;

import com.campusguinness.activity.application.query.ActivityQueryService;
import com.campusguinness.activity.application.result.ActivityResult;
import com.campusguinness.activity.application.service.ActivityManagementService;
import com.campusguinness.activity.internal.domain.*;
import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SchoolAdminActivityController.class)
@AutoConfigureMockMvc(addFilters = false)
class SchoolAdminActivityControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ActivityManagementService service;
    @MockitoBean ActivityQueryService queryService;
    @MockitoBean CurrentActor currentActor;
    @MockitoBean SchoolMembershipQueryPort membershipPort;
    @Autowired ObjectMapper mapper;

    UUID userId = UUID.randomUUID();
    UUID schoolId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();

    void givenSchoolAdmin() {
        when(currentActor.requireUserId()).thenReturn(userId);
        when(membershipPort.findActiveSchoolAdminSchoolId(userId)).thenReturn(Optional.of(schoolId));
    }

    @Test void createReturns201() throws Exception {
        givenSchoolAdmin();
        when(service.create(any())).thenReturn(new ActivityResult(activityId, "DRAFT", "NOT_SUBMITTED"));
        mvc.perform(post("/api/v1/school-admin/activities")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Test Activity\",\"description\":\"desc\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activityId").value(activityId.toString()))
                .andExpect(jsonPath("$.executionStatus").value("DRAFT"));
    }

    @Test void createRequiresSchoolAdminMembership() throws Exception {
        when(currentActor.requireUserId()).thenReturn(userId);
        when(membershipPort.findActiveSchoolAdminSchoolId(userId)).thenReturn(Optional.empty());
        mvc.perform(post("/api/v1/school-admin/activities")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test void listShowsOnlyOwnSchool() throws Exception {
        givenSchoolAdmin();
        when(queryService.listBySchool(eq(schoolId), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(new com.campusguinness.project.application.query.model.QueryPage<>(
                        Collections.emptyList(), 0, 20, 0));
        mvc.perform(get("/api/v1/school-admin/activities"))
                .andExpect(status().isOk());
    }

    @Test void listWithFilters() throws Exception {
        givenSchoolAdmin();
        when(queryService.listBySchool(eq(schoolId), eq("DRAFT"), eq("NOT_SUBMITTED"), any(), eq(0), eq(20)))
                .thenReturn(new com.campusguinness.project.application.query.model.QueryPage<>(
                        Collections.emptyList(), 0, 20, 0));
        mvc.perform(get("/api/v1/school-admin/activities?executionStatus=DRAFT&publicStatus=NOT_SUBMITTED"))
                .andExpect(status().isOk());
    }

    @Test void listRejectsNegativePage() throws Exception {
        givenSchoolAdmin();
        when(queryService.listBySchool(any(), any(), any(), any(), eq(-1), eq(20)))
                .thenThrow(new IllegalArgumentException("page must be >= 0"));
        mvc.perform(get("/api/v1/school-admin/activities?page=-1"))
                .andExpect(status().isBadRequest());
    }

    @Test void detailReturnsOwnSchoolActivity() throws Exception {
        givenSchoolAdmin();
        var act = draftActivity();
        when(service.findById(activityId)).thenReturn(act);
        when(service.listProjects(activityId)).thenReturn(Collections.emptyList());
        mvc.perform(get("/api/v1/school-admin/activities/" + activityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityId").value(activityId.toString()));
    }

    @Test void detailRejectsOtherSchool() throws Exception {
        givenSchoolAdmin();
        UUID otherSchoolId = UUID.randomUUID();
        var act = Activity.create(new Activity.Builder()
                .id(new ActivityId(activityId)).schoolId(otherSchoolId)
                .createdBy(UUID.randomUUID()).title("t"));
        when(service.findById(activityId)).thenReturn(act);
        mvc.perform(get("/api/v1/school-admin/activities/" + activityId))
                .andExpect(status().isNotFound());
    }

    @Test void updateDraftReturns200() throws Exception {
        givenSchoolAdmin();
        var act = draftActivity();
        when(service.findById(activityId)).thenReturn(act);
        when(service.update(eq(activityId), any(), any(), any(), any(), any()))
                .thenReturn(new ActivityResult(activityId, "DRAFT", "NOT_SUBMITTED"));
        mvc.perform(patch("/api/v1/school-admin/activities/" + activityId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Updated Title\"}"))
                .andExpect(status().isOk());
    }

    @Test void publishReturns200() throws Exception {
        givenSchoolAdmin();
        var act = draftActivity();
        when(service.findById(activityId)).thenReturn(act);
        when(service.publish(activityId)).thenReturn(new ActivityResult(activityId, "PUBLISHED", "NOT_SUBMITTED"));
        mvc.perform(post("/api/v1/school-admin/activities/" + activityId + "/publish"))
                .andExpect(status().isOk());
    }

    @Test void startReturns200() throws Exception {
        givenSchoolAdmin();
        var act = draftActivity();
        when(service.findById(activityId)).thenReturn(act);
        when(service.beginExecution(activityId)).thenReturn(new ActivityResult(activityId, "IN_PROGRESS", "NOT_SUBMITTED"));
        mvc.perform(post("/api/v1/school-admin/activities/" + activityId + "/start"))
                .andExpect(status().isOk());
    }

    @Test void submitPublicReviewReturns200() throws Exception {
        givenSchoolAdmin();
        var act = draftActivity();
        when(service.findById(activityId)).thenReturn(act);
        when(service.submitForPublicReview(activityId)).thenReturn(new ActivityResult(activityId, "PUBLISHED", "PENDING_PLATFORM_REVIEW"));
        mvc.perform(post("/api/v1/school-admin/activities/" + activityId + "/submit-public-review"))
                .andExpect(status().isOk());
    }

    @Test void finishReturns200() throws Exception {
        givenSchoolAdmin();
        var act = draftActivity();
        when(service.findById(activityId)).thenReturn(act);
        when(service.finish(activityId)).thenReturn(new ActivityResult(activityId, "ENDED", "NOT_SUBMITTED"));
        mvc.perform(post("/api/v1/school-admin/activities/" + activityId + "/finish"))
                .andExpect(status().isOk());
    }

    private Activity draftActivity() {
        return Activity.create(new Activity.Builder()
                .id(new ActivityId(activityId)).schoolId(schoolId)
                .createdBy(userId).title("Test Activity"));
    }
}
