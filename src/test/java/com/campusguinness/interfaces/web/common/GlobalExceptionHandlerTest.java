package com.campusguinness.interfaces.web.common;

import com.campusguinness.interfaces.web.challengeproject.ChallengeProjectController;
import com.campusguinness.project.application.query.ChallengeProjectQueryService;
import com.campusguinness.project.application.service.ChallengeProjectApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChallengeProjectController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ChallengeProjectApplicationService service;
    @MockitoBean ChallengeProjectQueryService queryService;

    @Test void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/v1/challenge-projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test void notFoundReturns404() throws Exception {
        when(service.findById(any())).thenThrow(new IllegalArgumentException("ChallengeProject not found: x"));
        mvc.perform(get("/api/v1/challenge-projects/00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test void duplicateReturns409() throws Exception {
        when(service.findById(any())).thenThrow(new IllegalArgumentException("already disabled"));
        mvc.perform(get("/api/v1/challenge-projects/00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test void stateConflictReturns409() throws Exception {
        when(service.findById(any())).thenThrow(new IllegalStateException("Cannot publish from status"));
        mvc.perform(get("/api/v1/challenge-projects/00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test void persistenceCorruptionReturns500() throws Exception {
        when(service.findById(any())).thenThrow(
                new com.campusguinness.score.internal.persistence.ScoreValuePersistenceException("INTEGER score_value is null"));
        mvc.perform(get("/api/v1/challenge-projects/00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_DATA_CORRUPTION"))
                .andExpect(jsonPath("$.message").value("Stored data could not be restored safely."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").exists());
    }

    @Test void unknownExceptionReturns500() throws Exception {
        when(service.findById(any())).thenThrow(new RuntimeException("boom"));
        mvc.perform(get("/api/v1/challenge-projects/00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
