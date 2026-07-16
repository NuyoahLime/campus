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
import org.springframework.http.MediaType;
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

    @Nested class Create {
        @Test void shouldReturn201() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.create(any())).thenReturn(new ActivityResult(id, "DRAFT", "NOT_SUBMITTED"));
            mvc.perform(post("/api/v1/activities").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(new CreateActivityRequest(UUID.randomUUID(), UUID.randomUUID(), "t", "d", null, null, null))))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.executionStatus").value("DRAFT"));
        }
    }
    @Nested class Publish {
        @Test void shouldReturn200() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.publish(id)).thenReturn(new ActivityResult(id, "PUBLISHED", "NOT_SUBMITTED"));
            mvc.perform(post("/api/v1/activities/" + id + "/publish"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.executionStatus").value("PUBLISHED"));
        }
    }
    @Nested class ListQuery {
        @Test void shouldReturn200() throws Exception {
            java.util.List<com.campusguinness.activity.application.query.model.ActivityListResult> empty = java.util.Collections.emptyList();
            when(queryService.listPublic(0, 20)).thenReturn(new com.campusguinness.project.application.query.model.QueryPage<>(empty, 0, 20, 0));
            mvc.perform(get("/api/v1/activities")).andExpect(status().isOk());
        }
        @Test void negativePageReturns400() throws Exception {
            when(queryService.listPublic(-1, 20)).thenThrow(new IllegalArgumentException("page must be >= 0"));
            mvc.perform(get("/api/v1/activities?page=-1")).andExpect(status().isBadRequest());
        }
        @Test void listItemExcludesInternalFields() throws Exception {
            var r = new com.campusguinness.activity.application.query.model.ActivityListResult(UUID.randomUUID(), UUID.randomUUID(), "t", java.time.Instant.now(), java.time.Instant.now(), "loc", "PUBLISHED");
            when(queryService.listPublic(0, 20)).thenReturn(new com.campusguinness.project.application.query.model.QueryPage<>(java.util.List.of(r), 0, 20, 1));
            mvc.perform(get("/api/v1/activities"))
                    .andExpect(jsonPath("$.items[0].description").doesNotExist())
                    .andExpect(jsonPath("$.items[0].createdBy").doesNotExist())
                    .andExpect(jsonPath("$.items[0].publicStatus").doesNotExist())
                    .andExpect(jsonPath("$.items[0].version").doesNotExist());
        }
        @Test void paginationMetadataCorrect() throws Exception {
            var r = new com.campusguinness.activity.application.query.model.ActivityListResult(UUID.randomUUID(), UUID.randomUUID(), "t", java.time.Instant.now(), java.time.Instant.now(), "loc", "PUBLISHED");
            when(queryService.listPublic(1, 2)).thenReturn(new com.campusguinness.project.application.query.model.QueryPage<>(java.util.List.of(r), 1, 2, 5));
            mvc.perform(get("/api/v1/activities?page=1&size=2"))
                    .andExpect(jsonPath("$.page").value(1))
                    .andExpect(jsonPath("$.size").value(2))
                    .andExpect(jsonPath("$.totalElements").value(5))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.hasNext").value(true));
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
