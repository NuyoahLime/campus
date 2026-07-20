package com.campusguinness.interfaces.web.l3authorization;

import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.ranking.application.result.L3AuthorizationResult;
import com.campusguinness.ranking.application.service.L3AuthorizationApplicationService;
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

@WebMvcTest(L3AuthorizationController.class)
@AutoConfigureMockMvc(addFilters = false)
class L3AuthorizationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean L3AuthorizationApplicationService service;
    @MockitoBean CurrentActor currentActor;
    @Autowired ObjectMapper mapper;
    private final UUID actorId = UUID.randomUUID();

    @Test void submitReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.submit(any(), any(), any())).thenReturn(new L3AuthorizationResult(id, "PENDING_REVIEW"));
        mvc.perform(post("/api/v1/l3-authorizations").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new CreateL3AuthorizationRequest(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    }
    @Test void approveReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(currentActor.requireUserId()).thenReturn(actorId);
        when(service.approve(eq(id), any(), any())).thenReturn(new L3AuthorizationResult(id, "APPROVED"));
        mvc.perform(post("/api/v1/l3-authorizations/" + id + "/approve").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new ApproveL3AuthorizationRequest("ok"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
    }
    @Test void withdrawReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.withdraw(eq(id), any())).thenReturn(new L3AuthorizationResult(id, "WITHDRAWN"));
        mvc.perform(post("/api/v1/l3-authorizations/" + id + "/withdraw").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new WithdrawL3AuthorizationRequest("reason"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }
    @Test void notFound() throws Exception {
        when(currentActor.requireUserId()).thenReturn(actorId);
        when(service.approve(any(), any(), any())).thenThrow(new IllegalArgumentException("not found"));
        mvc.perform(post("/api/v1/l3-authorizations/" + UUID.randomUUID() + "/approve").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new ApproveL3AuthorizationRequest("ok"))))
                .andExpect(status().isNotFound());
    }
}
