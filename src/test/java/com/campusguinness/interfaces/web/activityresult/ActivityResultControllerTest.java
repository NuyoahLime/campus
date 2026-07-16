package com.campusguinness.interfaces.web.activityresult;

import com.campusguinness.result.application.result.ActivityResultResult;
import com.campusguinness.result.application.service.ActivityResultApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ActivityResultController.class)
@AutoConfigureMockMvc(addFilters = false)
class ActivityResultControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ActivityResultApplicationService service;

    @Test void publishReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.publishInternal(id)).thenReturn(new ActivityResultResult(id, "INTERNAL_PUBLISHED", "NOT_SUBMITTED"));
        mvc.perform(post("/api/v1/activity-results/" + id + "/publish"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.internalStatus").value("INTERNAL_PUBLISHED"));
    }
    @Test void notFoundReturns404() throws Exception {
        when(service.publishInternal(any())).thenThrow(new IllegalArgumentException("not found"));
        mvc.perform(post("/api/v1/activity-results/" + UUID.randomUUID() + "/publish"))
                .andExpect(status().isNotFound());
    }
}
