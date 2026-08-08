package com.campusguinness.interfaces.web.feedback;

import com.campusguinness.feedback.application.result.FeedbackResult;
import com.campusguinness.feedback.application.service.FeedbackApplicationService;
import com.campusguinness.feedback.internal.domain.*;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FeedbackController.class)
@AutoConfigureMockMvc(addFilters = false)
class FeedbackControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean FeedbackApplicationService service;
    @Autowired ObjectMapper mapper;

    @Test void submitReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.submit(any(), anyString(), anyString())).thenReturn(new FeedbackResult(id, "SUBMITTED"));
        mvc.perform(post("/api/v1/feedbacks").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new SubmitFeedbackRequest(UUID.randomUUID(),"GENERAL","test content"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SUBMITTED"));
    }
    @Test void beginProcessingReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.beginProcessing(eq(id), any())).thenReturn(new FeedbackResult(id, "PROCESSING"));
        mvc.perform(post("/api/v1/feedbacks/" + id + "/begin-processing").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new BeginProcessingRequest(UUID.randomUUID()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING"));
    }
    @Test void resolveReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.resolve(eq(id), anyString())).thenReturn(new FeedbackResult(id, "RESOLVED"));
        mvc.perform(post("/api/v1/feedbacks/" + id + "/resolve").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new ResolveFeedbackRequest("done"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));
    }
    @Test void closeReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.close(eq(id), anyString())).thenReturn(new FeedbackResult(id, "CLOSED"));
        mvc.perform(post("/api/v1/feedbacks/" + id + "/close").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new CloseFeedbackRequest("spam"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
    }
    @Test void notFoundReturns404() throws Exception {
        when(service.beginProcessing(any(), any())).thenThrow(new IllegalArgumentException("not found"));
        mvc.perform(post("/api/v1/feedbacks/" + UUID.randomUUID() + "/begin-processing").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new BeginProcessingRequest(UUID.randomUUID()))))
                .andExpect(status().isNotFound());
    }
    @Test void conflictReturns409() throws Exception {
        when(service.resolve(any(), anyString())).thenThrow(new InvalidFeedbackStateTransitionException(FeedbackStatus.SUBMITTED, "resolve"));
        mvc.perform(post("/api/v1/feedbacks/" + UUID.randomUUID() + "/resolve").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new ResolveFeedbackRequest("done"))))
                .andExpect(status().isConflict());
    }
    @Test void responseExcludesInternalFields() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.submit(any(), anyString(), anyString())).thenReturn(new FeedbackResult(id, "SUBMITTED"));
        mvc.perform(post("/api/v1/feedbacks").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new SubmitFeedbackRequest(UUID.randomUUID(),"GENERAL","test"))))
                .andExpect(jsonPath("$.submitterId").doesNotExist()).andExpect(jsonPath("$.content").doesNotExist());
    }
}
