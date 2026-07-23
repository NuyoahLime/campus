package com.campusguinness.interfaces.web.activity;

import com.campusguinness.activity.application.query.ActivityQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ActivityController.class)
@AutoConfigureMockMvc(addFilters = false)
class ActivityControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ActivityQueryService queryService;
    @Autowired ObjectMapper mapper;

    @Nested class ListQuery {
        @Test void shouldReturn200() throws Exception {
            java.util.List<com.campusguinness.activity.application.query.model.ActivityListResult> empty = java.util.Collections.emptyList();
            when(queryService.listPublic(0, 20)).thenReturn(new com.campusguinness.project.application.query.model.QueryPage<>(empty, 0, 20, 0));
            mvc.perform(get("/api/v1/activities")).andExpect(status().isOk());
        }
    }
}
