package com.campusguinness.interfaces.web.ranking;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.ranking.application.query.model.RankingReadResult;
import com.campusguinness.ranking.application.query.model.RankingReadSummaryResult;
import com.campusguinness.ranking.application.service.RankingReadQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RankingReadController.class)
@AutoConfigureMockMvc(addFilters = false)
class RankingReadControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean RankingReadQueryService service;

    @Test
    void publicListReturnsPublishedSnapshotSummaries() throws Exception {
        UUID rankingId = UUID.randomUUID();
        when(service.listPublic(0, 20)).thenReturn(new QueryPage<>(
                List.of(summary(rankingId)), 0, 20, 1));

        mvc.perform(get("/api/v1/public/rankings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(rankingId.toString()))
                .andExpect(jsonPath("$.items[0].name").value("Published ranking"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void studentDetailReturnsStoredSnapshotEntries() throws Exception {
        UUID rankingId = UUID.randomUUID();
        when(service.studentDetail(rankingId)).thenReturn(new RankingReadResult(
                rankingId, "Published ranking", "L2", UUID.randomUUID(), "Campus School",
                UUID.randomUUID(), "Challenge", 3, Instant.parse("2026-08-21T00:00:00Z"),
                List.of(new com.campusguinness.ranking.application.query.model.RankingEntryReadResult(
                        1, "Student A", "Campus School", "99"))));

        mvc.perform(get("/api/v1/student/rankings/" + rankingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].rankPosition").value(1))
                .andExpect(jsonPath("$.entries[0].studentDisplayName").value("Student A"))
                .andExpect(jsonPath("$.entries[0].scoreDisplayValue").value("99"))
                .andExpect(jsonPath("$.entries[0].studentId").doesNotExist());
    }

    private RankingReadSummaryResult summary(UUID id) {
        return new RankingReadSummaryResult(
                id, "Published ranking", "L1", null, null, UUID.randomUUID(),
                "Challenge", 1, Instant.parse("2026-08-21T00:00:00Z"));
    }
}
