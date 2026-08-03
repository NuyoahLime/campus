package com.campusguinness.interfaces.web.scoreappeal;

import com.campusguinness.appeal.application.result.ScoreAppealResult;
import com.campusguinness.appeal.application.service.ScoreAppealApplicationService;
import com.campusguinness.appeal.internal.domain.*;
import com.campusguinness.infrastructure.security.ActorContext;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.infrastructure.security.CurrentActorContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScoreAppealController.class)
@AutoConfigureMockMvc(addFilters = false)
class ScoreAppealControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ScoreAppealApplicationService service;
    @MockitoBean CurrentActor currentActor;
    @MockitoBean CurrentActorContext actorContext;
    @Autowired ObjectMapper mapper;

    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID SCHOOL_ID = UUID.randomUUID();
    private static final ActorContext SCHOOL_ADMIN = new ActorContext(ACTOR_ID, "SCHOOL_ADMIN", SCHOOL_ID);

    @Test void submitReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(currentActor.requireUserId()).thenReturn(ACTOR_ID);
        when(service.submit(any(), eq(ACTOR_ID), anyString(), anyString())).thenReturn(new ScoreAppealResult(id, "SUBMITTED"));
        mvc.perform(post("/api/v1/score-appeals").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new SubmitScoreAppealRequest(UUID.randomUUID(),"SCORE","reason"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test void beginProcessingReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(actorContext.require()).thenReturn(SCHOOL_ADMIN);
        when(service.beginProcessing(id, SCHOOL_ADMIN)).thenReturn(new ScoreAppealResult(id, "PROCESSING"));
        mvc.perform(post("/api/v1/score-appeals/" + id + "/begin-processing"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING"));
        verify(service).beginProcessing(id, SCHOOL_ADMIN);
    }

    @Test void rejectReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(actorContext.require()).thenReturn(SCHOOL_ADMIN);
        when(service.reject(eq(id), eq(SCHOOL_ADMIN), anyString())).thenReturn(new ScoreAppealResult(id, "REJECTED"));
        mvc.perform(post("/api/v1/score-appeals/" + id + "/reject").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RejectScoreAppealRequest("reason"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test void withdrawReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(currentActor.requireUserId()).thenReturn(ACTOR_ID);
        when(service.withdraw(id, ACTOR_ID)).thenReturn(new ScoreAppealResult(id, "WITHDRAWN"));
        mvc.perform(post("/api/v1/score-appeals/" + id + "/withdraw"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }

    @Test void notFoundReturns404() throws Exception {
        when(actorContext.require()).thenReturn(SCHOOL_ADMIN);
        when(service.beginProcessing(any(), eq(SCHOOL_ADMIN))).thenThrow(new IllegalArgumentException("not found"));
        mvc.perform(post("/api/v1/score-appeals/" + UUID.randomUUID() + "/begin-processing"))
                .andExpect(status().isNotFound());
    }

    @Test void stateConflictReturns409() throws Exception {
        when(actorContext.require()).thenReturn(SCHOOL_ADMIN);
        when(service.beginProcessing(any(), eq(SCHOOL_ADMIN))).thenThrow(new InvalidAppealStateTransitionException(AppealStatus.RESOLVED, "begin processing"));
        mvc.perform(post("/api/v1/score-appeals/" + UUID.randomUUID() + "/begin-processing"))
                .andExpect(status().isConflict());
    }

    @Test void responseExcludesInternalFields() throws Exception {
        UUID id = UUID.randomUUID();
        when(currentActor.requireUserId()).thenReturn(ACTOR_ID);
        when(service.withdraw(id, ACTOR_ID)).thenReturn(new ScoreAppealResult(id, "WITHDRAWN"));
        mvc.perform(post("/api/v1/score-appeals/" + id + "/withdraw"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.handlerId").doesNotExist())
                .andExpect(jsonPath("$.studentId").doesNotExist());
    }
}
