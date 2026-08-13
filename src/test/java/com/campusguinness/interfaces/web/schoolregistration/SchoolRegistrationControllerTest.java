package com.campusguinness.interfaces.web.schoolregistration;

import com.campusguinness.school.application.result.SchoolRegistrationResult;
import com.campusguinness.school.application.query.SchoolRegistrationQueryService;
import com.campusguinness.school.application.query.exception.SchoolRegistrationNotFoundException;
import com.campusguinness.school.application.query.model.SchoolRegistrationDetailResult;
import com.campusguinness.school.application.query.model.SchoolRegistrationListResult;
import com.campusguinness.project.application.query.model.QueryPage;
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
import java.time.Instant;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SchoolRegistrationController.class)
@AutoConfigureMockMvc(addFilters = false)
class SchoolRegistrationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SchoolRegistrationApplicationService service;
    @MockitoBean SchoolRegistrationQueryService queryService;
    @Autowired ObjectMapper mapper;

    @Nested class Query {
        @Test void listMapsPaginationAndPublicFields() throws Exception {
            UUID id = UUID.randomUUID();
            Instant createdAt = Instant.parse("2026-08-13T02:00:00Z");
            var item = new SchoolRegistrationListResult(
                    id, "Campus UAT School", "UNIVERSITY", "Zhejiang",
                    "Contact", "SUBMITTED", createdAt);
            when(queryService.list(0, 20, "SUBMITTED"))
                    .thenReturn(new QueryPage<>(List.of(item), 0, 20, 1));

            mvc.perform(get("/api/v1/school-registrations?status=SUBMITTED"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.items[0].id").value(id.toString()))
                    .andExpect(jsonPath("$.items[0].schoolName").value("Campus UAT School"))
                    .andExpect(jsonPath("$.items[0].status").value("SUBMITTED"))
                    .andExpect(jsonPath("$.items[0].createdAt").value(createdAt.toString()))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.totalPages").value(1))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }

        @Test void detailMapsReviewMetadataWithoutEvidenceKey() throws Exception {
            UUID id = UUID.randomUUID();
            UUID reviewerId = UUID.randomUUID();
            Instant createdAt = Instant.parse("2026-08-12T02:00:00Z");
            Instant reviewedAt = Instant.parse("2026-08-13T02:00:00Z");
            var detail = new SchoolRegistrationDetailResult(
                    id, "Campus UAT School", "USCC", "91330100UAT", "UNIVERSITY",
                    "Zhejiang", "A long address", "Contact", "13800000000", "uat@example.com",
                    "Registration description", true, "REJECTED", null, reviewerId, reviewedAt,
                    "Review comment", "Missing information", createdAt, reviewedAt);
            when(queryService.detail(id)).thenReturn(detail);

            mvc.perform(get("/api/v1/school-registrations/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.contactEmail").value("uat@example.com"))
                    .andExpect(jsonPath("$.evidenceSubmitted").value(true))
                    .andExpect(jsonPath("$.reviewedBy").value(reviewerId.toString()))
                    .andExpect(jsonPath("$.rejectReason").value("Missing information"))
                    .andExpect(jsonPath("$.evidenceFileKey").doesNotExist());
        }

        @Test void invalidQueryReturns400AndUnknownDetailUsesStable404() throws Exception {
            UUID id = UUID.randomUUID();
            when(queryService.list(-1, 20, null))
                    .thenThrow(new IllegalArgumentException("page must be >= 0"));
            when(queryService.detail(id)).thenThrow(new SchoolRegistrationNotFoundException(id));

            mvc.perform(get("/api/v1/school-registrations?page=-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
            mvc.perform(get("/api/v1/school-registrations/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SCHOOL_REGISTRATION_NOT_FOUND"));
        }
    }

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
            when(service.approve(eq(id), any(), eq(sid))).thenReturn(new SchoolRegistrationResult(id, "t", "APPROVED", sid));
            mvc.perform(post("/api/v1/school-registrations/" + id + "/approve").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(new ApproveSchoolRegistrationRequest("ok", sid))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        }
    }
    @Nested class Reject {
        @Test void shouldReturn200() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.reject(eq(id), any())).thenReturn(new SchoolRegistrationResult(id, "t", "REJECTED", null));
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
            when(service.approve(any(), any(), any())).thenThrow(new IllegalArgumentException("not found"));
            mvc.perform(post("/api/v1/school-registrations/" + UUID.randomUUID() + "/approve").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(new ApproveSchoolRegistrationRequest("ok", UUID.randomUUID()))))
                    .andExpect(status().isNotFound());
        }
    }
}
