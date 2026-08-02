package com.campusguinness.interfaces.web.rankingdefinition;

import com.campusguinness.ranking.application.result.RankingDefinitionResult;
import com.campusguinness.ranking.application.service.RankingDefinitionApplicationService;
import com.campusguinness.ranking.internal.domain.RankingLayer;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RankingDefinitionController.class)
@AutoConfigureMockMvc(addFilters = false)
class RankingDefinitionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean RankingDefinitionApplicationService service;
    @MockitoBean com.campusguinness.infrastructure.security.CurrentActor currentActor;
    @Autowired ObjectMapper mapper;

    @Test void createReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(currentActor.requireUserId()).thenReturn(UUID.randomUUID());
        when(service.create(any(), anyString(), any(), any(), any())).thenReturn(new RankingDefinitionResult(id, true));
        mvc.perform(post("/api/v1/ranking-definitions").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new CreateRankingDefinitionRequest("L1","test",null,UUID.randomUUID()))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.enabled").value(true));
    }
    @Test void enableReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.enable(id)).thenReturn(new RankingDefinitionResult(id, true));
        mvc.perform(post("/api/v1/ranking-definitions/" + id + "/enable")).andExpect(status().isOk());
    }
    @Test void disableReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.disable(id)).thenReturn(new RankingDefinitionResult(id, false));
        mvc.perform(post("/api/v1/ranking-definitions/" + id + "/disable")).andExpect(status().isOk());
    }
    @Test void notFound() throws Exception {
        when(service.enable(any())).thenThrow(new IllegalArgumentException("not found"));
        mvc.perform(post("/api/v1/ranking-definitions/" + UUID.randomUUID() + "/enable")).andExpect(status().isNotFound());
    }
}
