package com.campusguinness.interfaces.web.challengeproject;

import com.campusguinness.project.application.query.ChallengeProjectQueryService;
import com.campusguinness.project.application.query.model.ChallengeProjectListResult;
import com.campusguinness.project.application.result.ChallengeProjectResult;
import com.campusguinness.project.application.service.ChallengeProjectApplicationService;
import com.campusguinness.project.internal.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChallengeProjectController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
class ChallengeProjectControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ChallengeProjectApplicationService service;
    @MockitoBean ChallengeProjectQueryService queryService;
    @Autowired ObjectMapper mapper;

    @Nested class Create {
        @Test void shouldReturn201() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.create(any())).thenReturn(new ChallengeProjectResult(id, "test", "DRAFT"));
            mvc.perform(post("/api/v1/challenge-projects")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(validRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/v1/challenge-projects/" + id))
                    .andExpect(jsonPath("$.status").value("DRAFT"));
        }
        @Test void shouldReturn400WhenNameBlank() throws Exception {
            mvc.perform(post("/api/v1/challenge-projects")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(new CreateChallengeProjectRequest("","MATH","INTEGER","NUMERIC","HIGHER_BETTER","BEST",false,null,null,null,null,null))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Nested class Get {
        @Test void shouldReturn200() throws Exception {
            UUID id = UUID.randomUUID();
            var detail = new com.campusguinness.project.application.query.model.ChallengeProjectDetailResult(
                    id, "test", "MATH", "desc", null, null, "rules", "INTEGER", "NUMERIC",
                    "HIGHER_BETTER", null, null, null, false, "BEST", "PUBLISHED", null, null, null, null);
            when(queryService.publicDetail(id)).thenReturn(detail);
            mvc.perform(get("/api/v1/challenge-projects/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id.toString()));
        }
        @Test void shouldReturn404() throws Exception {
            UUID id = UUID.randomUUID();
            when(queryService.publicDetail(id)).thenThrow(new IllegalArgumentException("ChallengeProject not found: " + id));
            mvc.perform(get("/api/v1/challenge-projects/" + id))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested class Publish {
        @Test void shouldReturn200() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.publish(id, "Initial release"))
                    .thenReturn(new ChallengeProjectResult(id, "test", "PUBLISHED"));
            mvc.perform(post("/api/v1/challenge-projects/" + id + "/publish")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"Initial release\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PUBLISHED"));
        }

        @Test void shouldRejectMissingBody() throws Exception {
            mvc.perform(post("/api/v1/challenge-projects/" + UUID.randomUUID() + "/publish"))
                    .andExpect(status().isBadRequest());
        }

        @Test void shouldRejectBlankReason() throws Exception {
            mvc.perform(post("/api/v1/challenge-projects/" + UUID.randomUUID() + "/publish")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\" \"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test void shouldRejectEmptyObject() throws Exception {
            mvc.perform(post("/api/v1/challenge-projects/" + UUID.randomUUID() + "/publish")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test void shouldRejectReasonOutsideLengthBounds() throws Exception {
            mvc.perform(post("/api/v1/challenge-projects/" + UUID.randomUUID() + "/publish")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"x\"}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(post("/api/v1/challenge-projects/" + UUID.randomUUID() + "/publish")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"" + "x".repeat(501) + "\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested class ListQuery {
        @Test void shouldReturn200() throws Exception {
            java.util.List<ChallengeProjectListResult> empty = java.util.Collections.emptyList();
            when(queryService.listPublic(0, 20)).thenReturn(new com.campusguinness.project.application.query.model.QueryPage<>(empty, 0, 20, 0));
            mvc.perform(get("/api/v1/challenge-projects")).andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray());
        }
        @Test void negativePageReturns400() throws Exception {
            when(queryService.listPublic(-1, 20)).thenThrow(new IllegalArgumentException("page must be >= 0"));
            mvc.perform(get("/api/v1/challenge-projects?page=-1")).andExpect(status().isBadRequest());
        }
        @Test void zeroSizeReturns400() throws Exception {
            when(queryService.listPublic(0, 0)).thenThrow(new IllegalArgumentException("size must be between 1 and 100"));
            mvc.perform(get("/api/v1/challenge-projects?size=0")).andExpect(status().isBadRequest());
        }
        @Test void excessiveSizeReturns400() throws Exception {
            when(queryService.listPublic(0, 101)).thenThrow(new IllegalArgumentException("size must be between 1 and 100"));
            mvc.perform(get("/api/v1/challenge-projects?size=101")).andExpect(status().isBadRequest());
        }
        @Test void listItemExcludesInternalFields() throws Exception {
            var result = new ChallengeProjectListResult(UUID.randomUUID(), "t", "MATH", "INTEGER", "HIGHER_BETTER", "PUBLISHED", java.time.Instant.now());
            when(queryService.listPublic(0, 20)).thenReturn(new com.campusguinness.project.application.query.model.QueryPage<>(java.util.List.of(result), 0, 20, 1));
            mvc.perform(get("/api/v1/challenge-projects"))
                    .andExpect(jsonPath("$.items[0].description").doesNotExist())
                    .andExpect(jsonPath("$.items[0].version").doesNotExist())
                    .andExpect(jsonPath("$.items[0].rulesText").doesNotExist())
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }
    }

    private CreateChallengeProjectRequest validRequest() {
        return new CreateChallengeProjectRequest("校园数学挑战赛","MATH","INTEGER","NUMERIC","HIGHER_BETTER","BEST",false,"次",null,null,null,"desc");
    }
}
