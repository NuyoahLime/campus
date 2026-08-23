package com.campusguinness.interfaces.web.activity;

import com.campusguinness.activity.application.query.model.ActivityDetailResult;
import com.campusguinness.activity.application.query.model.ActivityListResult;
import com.campusguinness.activity.application.service.ActivityParticipantService;
import com.campusguinness.project.application.query.model.QueryPage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentAssignedActivityController.class)
@AutoConfigureMockMvc(addFilters = false)
class StudentAssignedActivityControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ActivityParticipantService service;

    @Test
    void listsAssignedActivitiesWithPagination() throws Exception {
        UUID activityId = UUID.randomUUID();
        var item = new ActivityListResult(activityId, UUID.randomUUID(), "Activity", null, null,
                "Gym", "PUBLISHED", "School", "Region", "Description");
        when(service.listAssigned(0, 20)).thenReturn(new QueryPage<>(List.of(item), 0, 20, 1));

        mvc.perform(get("/api/v1/student/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(activityId.toString()))
                .andExpect(jsonPath("$.items[0].title").value("Activity"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void returnsAssignedActivityDetail() throws Exception {
        UUID activityId = UUID.randomUUID();
        var detail = new ActivityDetailResult(activityId, UUID.randomUUID(), "School", "Region",
                "Activity", "Description", null, null, "Gym", "PUBLISHED", List.of());
        when(service.assignedDetail(activityId)).thenReturn(detail);

        mvc.perform(get("/api/v1/student/activities/{id}", activityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(activityId.toString()))
                .andExpect(jsonPath("$.title").value("Activity"))
                .andExpect(jsonPath("$.projects").isArray());
    }
}
