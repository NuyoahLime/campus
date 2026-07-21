package com.campusguinness.interfaces.web.scoreappeal;

import com.campusguinness.appeal.application.result.ScoreAppealResult;
import com.campusguinness.appeal.application.service.ScoreAppealApplicationService;
import com.campusguinness.appeal.application.service.ScoreAppealCorrectionService;
import com.campusguinness.appeal.internal.domain.*;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.infrastructure.security.SchoolMembershipResolver;
import com.campusguinness.score.internal.domain.ScoreValue;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScoreAppealController.class)
@AutoConfigureMockMvc(addFilters = false)
class ScoreAppealControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ScoreAppealApplicationService service;
    @MockitoBean ScoreAppealCorrectionService correctionService;
    @MockitoBean CurrentActor currentActor;
    @MockitoBean SchoolMembershipResolver membershipResolver;
    @Autowired ObjectMapper mapper;
    private final UUID actorId = UUID.randomUUID();

    @Test void submitReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(currentActor.requireUserId()).thenReturn(actorId);
        when(service.submitAuthorized(any(), any(), any(), any(), anyString(), anyString()))
            .thenReturn(new ScoreAppealResult(id, "SUBMITTED"));
        mvc.perform(post("/api/v1/score-appeals").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new SubmitScoreAppealRequest(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"SCORE","reason"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SUBMITTED")).andExpect(jsonPath("$.id").exists());
    }
    @Test void beginProcessingReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(currentActor.requireUserId()).thenReturn(actorId);
        when(service.beginProcessing(eq(id), any())).thenReturn(new ScoreAppealResult(id, "PROCESSING"));
        mvc.perform(post("/api/v1/score-appeals/" + id + "/begin-processing").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new BeginProcessingRequest())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING"));
    }
    @Test void rejectReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        var a = ScoreAppeal.create(new ScoreAppeal.Builder().id(new ScoreAppealId(id)).schoolId(UUID.randomUUID()).scoreAttemptId(UUID.randomUUID()).studentId(UUID.randomUUID()).appealType("SCORE").appealReason("r"));
        a.beginProcessing(UUID.randomUUID());
        when(service.reject(eq(id), anyString())).thenReturn(new ScoreAppealResult(id, "REJECTED"));
        mvc.perform(post("/api/v1/score-appeals/" + id + "/reject").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RejectScoreAppealRequest("reason"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
    }
    @Test void withdrawReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.withdraw(id)).thenReturn(new ScoreAppealResult(id, "WITHDRAWN"));
        mvc.perform(post("/api/v1/score-appeals/" + id + "/withdraw"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }
    @Test void withdrawDeniedReturns403() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.withdraw(id)).thenThrow(new org.springframework.security.access.AccessDeniedException("not owner"));
        mvc.perform(post("/api/v1/score-appeals/" + id + "/withdraw"))
                .andExpect(status().isForbidden());
    }
    @Test void notFoundReturns404() throws Exception {
        when(currentActor.requireUserId()).thenReturn(actorId);
        when(service.beginProcessing(any(), any())).thenThrow(new IllegalArgumentException("not found"));
        mvc.perform(post("/api/v1/score-appeals/" + UUID.randomUUID() + "/begin-processing").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new BeginProcessingRequest())))
                .andExpect(status().isNotFound());
    }
    @Test void stateConflictReturns409() throws Exception {
        when(currentActor.requireUserId()).thenReturn(actorId);
        when(service.beginProcessing(any(), any())).thenThrow(new InvalidAppealStateTransitionException(AppealStatus.RESOLVED, "begin processing"));
        mvc.perform(post("/api/v1/score-appeals/" + UUID.randomUUID() + "/begin-processing").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new BeginProcessingRequest())))
                .andExpect(status().isConflict());
    }
    @Test void correctAndResolveReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(currentActor.requireUserId()).thenReturn(actorId);
        doNothing().when(correctionService).correctAndResolve(any(), any(), anyString(), any());
        mvc.perform(post("/api/v1/score-appeals/" + id + "/correct-and-resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scoreStorageType\":\"INTEGER\",\"integerValue\":95,\"resolution\":\"corrected\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test void listPendingReturns200() throws Exception {
        UUID schoolId = UUID.randomUUID();
        when(currentActor.requireUserId()).thenReturn(actorId);
        when(currentActor.isSuperAdmin()).thenReturn(true);
        when(service.findPendingBySchool(schoolId)).thenReturn(java.util.List.of());
        mvc.perform(get("/api/v1/score-appeals").param("schoolId", schoolId.toString()))
                .andExpect(status().isOk());
    }

    @Test void responseExcludesInternalFields() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.submit(any(), any(), any(), anyString(), anyString())).thenReturn(new ScoreAppealResult(id, "SUBMITTED"));
        mvc.perform(post("/api/v1/score-appeals").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new SubmitScoreAppealRequest(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"SCORE","reason"))))
                .andExpect(jsonPath("$.handlerId").doesNotExist()).andExpect(jsonPath("$.studentId").doesNotExist());
    }
}
