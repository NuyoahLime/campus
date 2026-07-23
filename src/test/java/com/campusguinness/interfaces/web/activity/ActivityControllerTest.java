package com.campusguinness.interfaces.web.activity;

import com.campusguinness.activity.application.query.ActivityQueryService;
import com.campusguinness.activity.application.result.ActivityResult;
import com.campusguinness.activity.application.service.ActivityManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ActivityController.class)
@AutoConfigureMockMvc(addFilters = false)
class ActivityControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ActivityManagementService service;
    @MockitoBean ActivityQueryService queryService;
    @Autowired ObjectMapper mapper;

    @Nested class Publish {
        @Test void shouldReturn200() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.publish(id)).thenReturn(new ActivityResult(id, "PUBLISHED", "NOT_SUBMITTED"));
            mvc.perform(post("/api/v1/activities/" + id + "/publish"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.executionStatus").value("PUBLISHED"));
        }
    }
    @Nested class Finish {
        @Test void shouldReturn200() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.finish(id)).thenReturn(new ActivityResult(id, "ENDED", "NOT_SUBMITTED"));
            mvc.perform(post("/api/v1/activities/" + id + "/finish"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.executionStatus").value("ENDED"));
        }
    }
    @Nested class Cancel {
        @Test void shouldReturn200() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.cancel(id)).thenReturn(new ActivityResult(id, "CANCELLED", "NOT_SUBMITTED"));
            mvc.perform(post("/api/v1/activities/" + id + "/cancel"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.executionStatus").value("CANCELLED"));
        }
    }
    @Nested class ListQuery {
        @Test void shouldReturn200() throws Exception {
            java.util.List<com.campusguinness.activity.application.query.model.ActivityListResult> empty = java.util.Collections.emptyList();
            when(queryService.listPublic(0, 20)).thenReturn(new com.campusguinness.project.application.query.model.QueryPage<>(empty, 0, 20, 0));
            mvc.perform(get("/api/v1/activities")).andExpect(status().isOk());
        }
    }
    @Nested class Errors {
        @Test void notFound() throws Exception {
            when(service.publish(any())).thenThrow(new IllegalArgumentException("not found"));
            mvc.perform(post("/api/v1/activities/" + UUID.randomUUID() + "/publish"))
                    .andExpect(status().isNotFound());
        }
    }
}
