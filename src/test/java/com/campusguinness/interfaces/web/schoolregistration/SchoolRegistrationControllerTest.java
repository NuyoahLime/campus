package com.campusguinness.interfaces.web.schoolregistration;

import com.campusguinness.school.application.result.SchoolRegistrationResult;
import com.campusguinness.school.application.service.SchoolRegistrationApplicationService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SchoolRegistrationController.class)
@AutoConfigureMockMvc(addFilters = false)
class SchoolRegistrationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SchoolRegistrationApplicationService service;
    @MockitoBean com.campusguinness.infrastructure.security.CurrentActor currentActor;
    @Autowired ObjectMapper mapper;

    @Nested class Submit {
        @Test void shouldReturn201() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.submit(any())).thenReturn(new SchoolRegistrationResult(id, "test", "SUBMITTED", null));
            mvc.perform(post("/api/v1/school-registrations").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(new SubmitSchoolRegistrationRequest("t","USCC","c","PRIMARY","Beijing","addr","name","phone","email",null,null))))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SUBMITTED"));
        }
    }
    @Nested class Approve {
        @Test void shouldReturn200() throws Exception {
            UUID id = UUID.randomUUID(), sid = UUID.randomUUID();
            when(service.approve(eq(id), any(), any(), eq(sid))).thenReturn(new SchoolRegistrationResult(id, "t", "APPROVED", sid));
            mvc.perform(post("/api/v1/school-registrations/" + id + "/approve").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(new ApproveSchoolRegistrationRequest("ok", sid))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        }
    }
    @Nested class Reject {
        @Test void shouldReturn200() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.reject(eq(id), any(), any())).thenReturn(new SchoolRegistrationResult(id, "t", "REJECTED", null));
            mvc.perform(post("/api/v1/school-registrations/" + id + "/reject").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(new RejectSchoolRegistrationRequest("reason"))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        }
    }
    @Nested class Withdraw {
        @Test void shouldReturn200() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.withdraw(id)).thenReturn(new SchoolRegistrationResult(id, "t", "WITHDRAWN", null));
            mvc.perform(post("/api/v1/school-registrations/" + id + "/withdraw"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WITHDRAWN"));
        }
    }
    @Nested class Errors {
        @Test void notFound() throws Exception {
            when(service.approve(any(), any(), any(), any())).thenThrow(new IllegalArgumentException("not found"));
            mvc.perform(post("/api/v1/school-registrations/" + UUID.randomUUID() + "/approve").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(new ApproveSchoolRegistrationRequest("ok", UUID.randomUUID()))))
                    .andExpect(status().isNotFound());
        }
    }
}
