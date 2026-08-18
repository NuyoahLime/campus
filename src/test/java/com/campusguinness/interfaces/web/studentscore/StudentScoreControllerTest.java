package com.campusguinness.interfaces.web.studentscore;

import com.campusguinness.score.application.query.model.StudentScoreDetailResult;
import com.campusguinness.score.application.query.model.StudentScoreListResult;
import com.campusguinness.score.application.service.StudentScoreQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentScoreController.class)
@AutoConfigureMockMvc(addFilters = false)
class StudentScoreControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean StudentScoreQueryService service;

    @Test
    void listReturnsPageContractWithoutClientIdentityParameters() throws Exception {
        var result = new com.campusguinness.project.application.query.model.QueryPage<>(
                List.of(new StudentScoreListResult(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        "活动", "项目", 1, "INTEGER", "10", "次", Instant.now(), "APPROVED")),
                0, 20, 1);
        when(service.list(anyInt(), anyInt())).thenReturn(result);

        mvc.perform(get("/api/v1/student/scores").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void detailReturnsRuleSnapshot() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.detail(id)).thenReturn(new StudentScoreDetailResult(id, UUID.randomUUID(), UUID.randomUUID(),
                "活动", "项目", 1, "DURATION", "12500", "秒", Instant.now(), "APPROVED",
                UUID.randomUUID(), 1, "历史规则"));

        mvc.perform(get("/api/v1/student/scores/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleVersionNumber").value(1))
                .andExpect(jsonPath("$.rulesText").value("历史规则"));
    }
}
