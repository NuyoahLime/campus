package com.campusguinness.interfaces.web.rankingdefinition;

import com.campusguinness.ranking.application.result.RankingDefinitionResult;
import com.campusguinness.ranking.application.result.RankingGenerationResult;
import com.campusguinness.ranking.application.service.RankingGenerationApplicationService;
import com.campusguinness.ranking.application.service.RankingDefinitionApplicationService;
import com.campusguinness.ranking.application.result.RankingPublicationResult;
import com.campusguinness.ranking.application.service.RankingPublicationApplicationService;
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
    @MockitoBean RankingGenerationApplicationService generationService;
    @MockitoBean RankingPublicationApplicationService publicationService;
    @Autowired ObjectMapper mapper;

    @Test void createReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any(), anyString(), any(), any(), any())).thenReturn(new RankingDefinitionResult(id, true));
        mvc.perform(post("/api/v1/ranking-definitions").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new CreateRankingDefinitionRequest("L1","test",null,UUID.randomUUID(), UUID.randomUUID()))))
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

    @Test void generateReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(generationService.generate(id)).thenReturn(new RankingGenerationResult(id, UUID.randomUUID(), 1, 2, "GENERATED", java.time.Instant.now()));
        mvc.perform(post("/api/v1/ranking-definitions/" + id + "/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GENERATED"));
    }

    @Test void publishReturns200() throws Exception {
        UUID definitionId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        when(publicationService.publish(definitionId, versionId))
                .thenReturn(new RankingPublicationResult(definitionId, versionId, null, versionId, "PUBLISHED", java.time.Instant.now()));
        mvc.perform(post("/api/v1/ranking-definitions/" + definitionId + "/versions/" + versionId + "/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.currentVersionId").value(versionId.toString()));
    }
}
