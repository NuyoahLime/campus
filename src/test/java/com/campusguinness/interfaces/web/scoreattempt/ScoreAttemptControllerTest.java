package com.campusguinness.interfaces.web.scoreattempt;

import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.score.application.result.ScoreAttemptResult;
import com.campusguinness.score.application.service.ScoreAttemptApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScoreAttemptController.class)
@AutoConfigureMockMvc(addFilters = false)
class ScoreAttemptControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ScoreAttemptApplicationService service;
    @MockitoBean CurrentActor currentActor;
    @Autowired ObjectMapper mapper;
    private final UUID actorId = UUID.randomUUID();

    @Test void submitIntegerScoreReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(currentActor.requireUserId()).thenReturn(actorId);
        when(service.submit(any())).thenReturn(new ScoreAttemptResult(id, "PENDING_REVIEW", "INTEGER"));
        mvc.perform(post("/api/v1/score-attempts").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new SubmitScoreRequest(
                        UUID.randomUUID(), UUID.randomUUID(), 1, "INTEGER",
                        100L, null, null, null, null, null))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.scoreType").value("INTEGER"));
    }
}
