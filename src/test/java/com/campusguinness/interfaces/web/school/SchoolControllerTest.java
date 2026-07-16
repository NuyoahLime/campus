package com.campusguinness.interfaces.web.school;

import com.campusguinness.school.application.query.SchoolQueryService;
import com.campusguinness.school.application.result.SchoolResult;
import com.campusguinness.school.application.service.SchoolApplicationService;
import com.campusguinness.school.internal.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SchoolController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
class SchoolControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean SchoolApplicationService service;
    @MockitoBean SchoolQueryService queryService;
    @Autowired ObjectMapper mapper;

    @Nested class Get {
        @Test void shouldReturn200() throws Exception {
            UUID id = UUID.randomUUID();
            var school = School.create(new School.Builder().id(new SchoolId(id)).name("test").unifiedCodeType("USCC").unifiedCode("123").internalCode("INT-001").schoolType("PRIMARY").region("Beijing").address("addr").contactName("n").contactPhone("p").contactEmail("e"));
            when(service.findById(id)).thenReturn(school);
            mvc.perform(get("/api/v1/schools/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PENDING_ENABLE"));
        }
        @Test void shouldReturn404() throws Exception {
            when(service.findById(any())).thenThrow(new IllegalArgumentException("School not found"));
            mvc.perform(get("/api/v1/schools/" + UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested class Activate {
        @Test void shouldReturn200() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.activate(id)).thenReturn(new SchoolResult(id, "test", "NORMAL"));
            mvc.perform(post("/api/v1/schools/" + id + "/activate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("NORMAL"));
        }
    }

    @Nested class Disable {
        @Test void shouldReturn200() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.disable(eq(id), any())).thenReturn(new SchoolResult(id, "test", "DISABLED"));
            mvc.perform(post("/api/v1/schools/" + id + "/disable")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(new DisableSchoolRequest("violation"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DISABLED"));
        }
    }
    @Nested class ListQuery {
        @Test void shouldReturn200() throws Exception {
            java.util.List<com.campusguinness.school.application.query.model.SchoolListResult> empty = java.util.Collections.emptyList();
            when(queryService.listNormal(0, 20)).thenReturn(new com.campusguinness.project.application.query.model.QueryPage<>(empty, 0, 20, 0));
            mvc.perform(get("/api/v1/schools")).andExpect(status().isOk());
        }
        @Test void negativePageReturns400() throws Exception {
            when(queryService.listNormal(-1, 20)).thenThrow(new IllegalArgumentException("page must be >= 0"));
            mvc.perform(get("/api/v1/schools?page=-1")).andExpect(status().isBadRequest());
        }
        @Test void listItemExcludesInternalFields() throws Exception {
            var r = new com.campusguinness.school.application.query.model.SchoolListResult(UUID.randomUUID(), "t", "PRIMARY", "Beijing");
            when(queryService.listNormal(0, 20)).thenReturn(new com.campusguinness.project.application.query.model.QueryPage<>(java.util.List.of(r), 0, 20, 1));
            mvc.perform(get("/api/v1/schools"))
                    .andExpect(jsonPath("$.items[0].contactName").doesNotExist())
                    .andExpect(jsonPath("$.items[0].contactPhone").doesNotExist())
                    .andExpect(jsonPath("$.items[0].address").doesNotExist())
                    .andExpect(jsonPath("$.items[0].unifiedCode").doesNotExist())
                    .andExpect(jsonPath("$.items[0].version").doesNotExist());
        }
        @Test void paginationMetadataCorrect() throws Exception {
            var r = new com.campusguinness.school.application.query.model.SchoolListResult(UUID.randomUUID(), "t", "PRIMARY", "Beijing");
            when(queryService.listNormal(0, 20)).thenReturn(new com.campusguinness.project.application.query.model.QueryPage<>(java.util.List.of(r), 0, 20, 3));
            mvc.perform(get("/api/v1/schools"))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.totalPages").value(1))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }
    }
}
