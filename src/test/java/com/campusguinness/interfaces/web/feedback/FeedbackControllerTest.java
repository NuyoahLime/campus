package com.campusguinness.interfaces.web.feedback;

import com.campusguinness.feedback.application.result.FeedbackResult;
import com.campusguinness.feedback.application.service.FeedbackApplicationService;
import com.campusguinness.feedback.internal.domain.*;
import com.campusguinness.infrastructure.security.ActorContext;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FeedbackController.class)
@AutoConfigureMockMvc(addFilters = false)
class FeedbackControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean FeedbackApplicationService service;
    @MockitoBean com.campusguinness.infrastructure.security.CurrentActor currentActor;
    @MockitoBean com.campusguinness.infrastructure.security.CurrentActorContext actorContext;
    @Autowired ObjectMapper mapper;
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID SCHOOL_ID = UUID.randomUUID();
    private static final ActorContext ADMIN_A = new ActorContext(ADMIN_ID, "SCHOOL_ADMIN", SCHOOL_ID);

    @Test void listReturns200() throws Exception {
        UUID sid = UUID.randomUUID(); when(actorContext.require()).thenReturn(ADMIN_A);
        when(service.listManageable(ADMIN_A, sid)).thenReturn(java.util.List.of());
        mvc.perform(get("/api/v1/feedbacks").param("schoolId", sid.toString())).andExpect(status().isOk());
        verify(service).listManageable(ADMIN_A, sid); verify(actorContext).require();
    }
    @Test void submitReturns201() throws Exception {
        UUID id = UUID.randomUUID(); when(actorContext.require()).thenReturn(ADMIN_A);
        when(service.submit(ADMIN_A, "GENERAL", "test")).thenReturn(new FeedbackResult(id, "SUBMITTED"));
        mvc.perform(post("/api/v1/feedbacks").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(new SubmitFeedbackRequest("GENERAL","test")))).andExpect(status().isCreated());
        verify(service).submit(ADMIN_A, "GENERAL", "test"); verifyNoInteractions(currentActor);
    }
    @Test void beginProcessingReturns200() throws Exception {
        UUID id = UUID.randomUUID(); when(actorContext.require()).thenReturn(ADMIN_A);
        when(service.beginProcessing(id, ADMIN_A)).thenReturn(new FeedbackResult(id, "PROCESSING"));
        mvc.perform(post("/api/v1/feedbacks/" + id + "/begin-processing")).andExpect(status().isOk());
        verify(service).beginProcessing(id, ADMIN_A); verifyNoInteractions(currentActor);
    }
    @Test void resolveReturns200() throws Exception {
        UUID id = UUID.randomUUID(); when(actorContext.require()).thenReturn(ADMIN_A);
        when(service.resolve(id, ADMIN_A, "done")).thenReturn(new FeedbackResult(id, "RESOLVED"));
        mvc.perform(post("/api/v1/feedbacks/" + id + "/resolve").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(new ResolveFeedbackRequest("done")))).andExpect(status().isOk());
        verify(service).resolve(id, ADMIN_A, "done");
    }
    @Test void closeReturns200() throws Exception {
        UUID id = UUID.randomUUID(); when(actorContext.require()).thenReturn(ADMIN_A);
        when(service.close(id, ADMIN_A, "spam")).thenReturn(new FeedbackResult(id, "CLOSED"));
        mvc.perform(post("/api/v1/feedbacks/" + id + "/close").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(new CloseFeedbackRequest("spam")))).andExpect(status().isOk());
        verify(service).close(id, ADMIN_A, "spam");
    }
    @Test void notFoundReturns404() throws Exception {
        when(actorContext.require()).thenReturn(ADMIN_A);
        when(service.beginProcessing(any(), eq(ADMIN_A))).thenThrow(new IllegalArgumentException("not found"));
        mvc.perform(post("/api/v1/feedbacks/" + UUID.randomUUID() + "/begin-processing")).andExpect(status().isNotFound());
    }
    @Test void conflictReturns409() throws Exception {
        when(actorContext.require()).thenReturn(ADMIN_A);
        when(service.resolve(any(), eq(ADMIN_A), anyString())).thenThrow(new InvalidFeedbackStateTransitionException(FeedbackStatus.SUBMITTED, "resolve"));
        mvc.perform(post("/api/v1/feedbacks/" + UUID.randomUUID() + "/resolve").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(new ResolveFeedbackRequest("done")))).andExpect(status().isConflict());
    }
    @Test void responseExcludesInternalFields() throws Exception {
        UUID id = UUID.randomUUID(); when(actorContext.require()).thenReturn(ADMIN_A);
        when(service.submit(any(), anyString(), anyString())).thenReturn(new FeedbackResult(id, "SUBMITTED"));
        mvc.perform(post("/api/v1/feedbacks").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(new SubmitFeedbackRequest("GENERAL","test")))).andExpect(jsonPath("$.submitterId").doesNotExist()).andExpect(jsonPath("$.content").doesNotExist());
    }
}
