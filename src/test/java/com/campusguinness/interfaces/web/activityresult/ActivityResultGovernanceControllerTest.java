package com.campusguinness.interfaces.web.activityresult;

import com.campusguinness.result.application.result.ActivityResultResult;
import com.campusguinness.result.application.service.ActivityResultGovernanceApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SuperAdminActivityResultGovernanceController.class)
@AutoConfigureMockMvc(addFilters = false)
class ActivityResultGovernanceControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private ActivityResultGovernanceApplicationService service;

    @Test
    void takedownDelegatesResultAndReasonOnly() throws Exception {
        UUID resultId = UUID.randomUUID();
        when(service.takedown(resultId, "  emergency  ")).thenReturn(
                new ActivityResultResult(resultId, "INTERNAL_PUBLISHED", "PLATFORM_TAKEDOWN"));

        mvc.perform(post("/api/v1/super-admin/activity-results/{id}/takedown", resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  emergency  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(resultId.toString()))
                .andExpect(jsonPath("$.publicStatus").value("PLATFORM_TAKEDOWN"));

        verify(service).takedown(resultId, "  emergency  ");
    }

    @Test
    void nullBlankAndOversizedReasonsReturnControlledValidationErrors() throws Exception {
        UUID resultId = UUID.randomUUID();
        String path = "/api/v1/super-admin/activity-results/{id}/takedown";

        mvc.perform(post(path, resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post(path, resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post(path, resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + "x".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
