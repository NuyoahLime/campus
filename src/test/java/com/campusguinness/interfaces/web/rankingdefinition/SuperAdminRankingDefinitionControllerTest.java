package com.campusguinness.interfaces.web.rankingdefinition;

import com.campusguinness.ranking.application.result.RankingDefinitionResult;
import com.campusguinness.ranking.application.result.RankingGenerationResult;
import com.campusguinness.ranking.application.service.L3RankingDefinitionApplicationService;
import com.campusguinness.ranking.application.service.RankingGenerationApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SuperAdminRankingDefinitionController.class)
@AutoConfigureMockMvc(addFilters = false)
class SuperAdminRankingDefinitionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean L3RankingDefinitionApplicationService service;
    @MockitoBean RankingGenerationApplicationService generationService;

    @Test
    void createReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(anyString(), any(UUID.class), any(UUID.class))).thenReturn(new RankingDefinitionResult(id, true));
        mvc.perform(post("/api/v1/super-admin/ranking-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"L3","projectId":"%s","ruleVersionId":"%s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void generateReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(generationService.generate(id)).thenReturn(
                new RankingGenerationResult(id, UUID.randomUUID(), 1, 0, "GENERATED", java.time.Instant.now()));
        mvc.perform(post("/api/v1/super-admin/ranking-definitions/" + id + "/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GENERATED"));
    }
}
