package com.campusguinness.interfaces.web.scoreattempt;

import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.infrastructure.security.SchoolMembershipResolver;
import com.campusguinness.score.application.result.ScoreAttemptResult;
import com.campusguinness.score.application.service.ScoreAttemptApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScoreAttemptController.class)
@AutoConfigureMockMvc(addFilters = false)
class ScoreAttemptControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ScoreAttemptApplicationService service;
    @MockitoBean CurrentActor currentActor;
    @MockitoBean SchoolMembershipResolver membershipResolver;
    @Autowired ObjectMapper mapper;
    private final UUID actorId = UUID.randomUUID();

    @Nested
    class ListScores {
        @Test
        void superAdminCanList() throws Exception {
            UUID schoolId = UUID.randomUUID();
            when(currentActor.requireUserId()).thenReturn(actorId);
            when(currentActor.isSuperAdmin()).thenReturn(true);
            when(service.findBySchool(schoolId)).thenReturn(List.of());
            mvc.perform(get("/api/v1/score-attempts").param("schoolId", schoolId.toString()))
                    .andExpect(status().isOk());
        }

        @Test
        void returnsEmptyList() throws Exception {
            UUID schoolId = UUID.randomUUID();
            when(currentActor.requireUserId()).thenReturn(actorId);
            when(currentActor.isSuperAdmin()).thenReturn(true);
            when(service.findBySchool(schoolId)).thenReturn(List.of());
            mvc.perform(get("/api/v1/score-attempts").param("schoolId", schoolId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));
        }
    }

    @Nested
    class Approve {
        @Test void returns200() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.approve(id)).thenReturn(new ScoreAttemptResult(id, "APPROVED", "INTEGER"));
            mvc.perform(post("/api/v1/score-attempts/" + id + "/approve"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"));
        }
    }

    @Nested
    class Reject {
        @Test void returns200() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.reject(id, "reason")).thenReturn(new ScoreAttemptResult(id, "REJECTED", "INTEGER"));
            mvc.perform(post("/api/v1/score-attempts/" + id + "/reject")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(new RejectScoreRequest("reason"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"));
        }
        @Test void missingReasonReturns400() throws Exception {
            mvc.perform(post("/api/v1/score-attempts/" + UUID.randomUUID() + "/reject")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Submit {
        @Test void submitIntegerScoreReturns201() throws Exception {
            UUID id = UUID.randomUUID();
            when(currentActor.requireUserId()).thenReturn(actorId);
            when(membershipResolver.isTeacherOrAbove(any(), any())).thenReturn(true);
            when(service.submit(any())).thenReturn(new ScoreAttemptResult(id, "PENDING_REVIEW", "INTEGER"));
            mvc.perform(post("/api/v1/score-attempts").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(new SubmitScoreRequest(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1,"INTEGER",100L,null,null,null,null,"teacher"))))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.scoreType").value("INTEGER"));
        }
        @Test void submitDecimalScoreReturns201() throws Exception {
            UUID id = UUID.randomUUID();
            when(currentActor.requireUserId()).thenReturn(actorId);
            when(membershipResolver.isTeacherOrAbove(any(), any())).thenReturn(true);
            when(service.submit(any())).thenReturn(new ScoreAttemptResult(id, "PENDING_REVIEW", "DECIMAL"));
            mvc.perform(post("/api/v1/score-attempts").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(new SubmitScoreRequest(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1,"DECIMAL",null,new java.math.BigDecimal("98.76"),null,null,null,"teacher"))))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.scoreType").value("DECIMAL"));
        }
        @Test void submitDurationScoreReturns201() throws Exception {
            UUID id = UUID.randomUUID();
            when(currentActor.requireUserId()).thenReturn(actorId);
            when(membershipResolver.isTeacherOrAbove(any(), any())).thenReturn(true);
            when(service.submit(any())).thenReturn(new ScoreAttemptResult(id, "PENDING_REVIEW", "DURATION"));
            mvc.perform(post("/api/v1/score-attempts").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(new SubmitScoreRequest(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1,"DURATION",null,null,12500L,null,null,"teacher"))))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.scoreType").value("DURATION"));
        }
        @Test void submitGradeScoreReturns201() throws Exception {
            UUID id = UUID.randomUUID();
            when(currentActor.requireUserId()).thenReturn(actorId);
            when(membershipResolver.isTeacherOrAbove(any(), any())).thenReturn(true);
            when(service.submit(any())).thenReturn(new ScoreAttemptResult(id, "PENDING_REVIEW", "GRADE"));
            mvc.perform(post("/api/v1/score-attempts").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(new SubmitScoreRequest(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1,"GRADE",null,null,null,"优秀",null,"teacher"))))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.scoreType").value("GRADE"));
        }
    }
}
