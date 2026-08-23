package com.campusguinness.interfaces.web.activity;

import com.campusguinness.activity.application.query.model.ActivityParticipantResult;
import com.campusguinness.activity.application.service.ActivityParticipantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivityParticipantController.class)
@AutoConfigureMockMvc(addFilters = false)
class ActivityParticipantControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean ActivityParticipantService service;

    @Test
    void listsParticipantsWithoutExposingInternalFields() throws Exception {
        UUID activityId = UUID.randomUUID();
        when(service.list(activityId)).thenReturn(List.of(
                new ActivityParticipantResult(UUID.randomUUID(), "student", "S-001", "2026", "1班", null)));

        mvc.perform(get("/api/v1/school-admin/activities/{id}/participants", activityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("student"))
                .andExpect(jsonPath("$[0].studentNumber").value("S-001"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void assignsAndRemovesByStudentResourceId() throws Exception {
        UUID activityId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        mvc.perform(post("/api/v1/school-admin/activities/{id}/participants", activityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new ActivityParticipantController.AssignParticipantRequest(studentId))))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/school-admin/activities/{id}/participants/{studentId}", activityId, studentId))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsMissingStudentId() throws Exception {
        mvc.perform(post("/api/v1/school-admin/activities/{id}/participants", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
