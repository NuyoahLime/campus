package com.campusguinness.interfaces.web.media;

import com.campusguinness.media.application.result.MediaResult;
import com.campusguinness.media.application.service.MediaApplicationService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MediaController.class)
@AutoConfigureMockMvc(addFilters = false)
class MediaControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean MediaApplicationService service;
    @Autowired ObjectMapper mapper;

    @Nested class Register {
        @Test void returns201() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.register(any())).thenReturn(new MediaResult(id, "DRAFT", "NOT_SUBMITTED"));
            mvc.perform(post("/api/v1/media").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(new RegisterMediaRequest(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"key","f.jpg","IMAGE","JPG",100,null,null))))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.internalStatus").value("DRAFT"));
        }
    }
    @Nested class InternalReview {
        @Test void returns200() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.submitForInternalReview(id)).thenReturn(new MediaResult(id, "PENDING_INTERNAL_REVIEW", "NOT_SUBMITTED"));
            mvc.perform(post("/api/v1/media/" + id + "/internal-review"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.internalStatus").value("PENDING_INTERNAL_REVIEW"));
        }
    }
    @Nested class InternalApprove {
        @Test void returns200() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.approveInternal(id)).thenReturn(new MediaResult(id, "INTERNAL_APPROVED", "NOT_SUBMITTED"));
            mvc.perform(post("/api/v1/media/" + id + "/internal-approve"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.internalStatus").value("INTERNAL_APPROVED"));
        }
    }
    @Nested class Errors {
        @Test void notFound() throws Exception {
            when(service.submitForInternalReview(any())).thenThrow(new IllegalArgumentException("not found"));
            mvc.perform(post("/api/v1/media/" + UUID.randomUUID() + "/internal-review"))
                    .andExpect(status().isNotFound());
        }
    }
}
