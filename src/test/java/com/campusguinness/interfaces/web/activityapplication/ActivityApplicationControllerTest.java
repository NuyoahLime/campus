package com.campusguinness.interfaces.web.activityapplication;

import com.campusguinness.activity.application.result.ActivityApplicationResult;
import com.campusguinness.activity.application.service.ActivityApplicationService;
import com.campusguinness.infrastructure.security.CurrentActor;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ActivityApplicationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ActivityApplicationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ActivityApplicationService service;
    @MockitoBean CurrentActor currentActor;
    @Autowired ObjectMapper mapper;

    @Nested class Submit {
        @Test void shouldReturn201() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.submit(any())).thenReturn(new ActivityApplicationResult(id, "SUBMITTED", null));
            mvc.perform(post("/api/v1/activity-applications").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(new SubmitActivityApplicationRequest(UUID.randomUUID(), "t", "d"))))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SUBMITTED"));
        }
    }
    @Nested class Approve {
        @Test void shouldReturn200() throws Exception {
            UUID id = UUID.randomUUID(), aid = UUID.randomUUID();
            when(service.approve(any(), any(), any())).thenReturn(new ActivityApplicationResult(id, "APPROVED", aid));
            mvc.perform(post("/api/v1/activity-applications/" + id + "/approve").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(new ApproveActivityApplicationRequest(UUID.randomUUID(), aid))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.createdActivityId").value(aid.toString()));
        }
    }
    @Nested class Errors {
        @Test void notFound() throws Exception {
            when(service.approve(any(), any(), any())).thenThrow(new IllegalArgumentException("not found"));
            mvc.perform(post("/api/v1/activity-applications/" + UUID.randomUUID() + "/approve").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(new ApproveActivityApplicationRequest(UUID.randomUUID(), UUID.randomUUID()))))
                    .andExpect(status().isNotFound());
        }
    }
}
