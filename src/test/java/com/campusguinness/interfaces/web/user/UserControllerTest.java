package com.campusguinness.interfaces.web.user;

import com.campusguinness.identity.application.exception.UsernameAlreadyExistsException;
import com.campusguinness.identity.application.result.UserResult;
import com.campusguinness.identity.application.service.UserApplicationService;
import com.campusguinness.identity.internal.domain.*;
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

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserApplicationService service;

    @Test void createReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(eq("testuser"), anyString())).thenReturn(new UserResult(id, "testuser", "PENDING_ACTIVATION"));
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"testuser\",\"initialPassword\":\"password123\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/v1/users/" + id))
                .andExpect(jsonPath("$.status").value("PENDING_ACTIVATION"));
    }

    @Test void missingPasswordReturns400() throws Exception {
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"testuser\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void blankPasswordReturns400() throws Exception {
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"testuser\",\"initialPassword\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void shortPasswordReturns400() throws Exception {
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"testuser\",\"initialPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void createDuplicateReturns409() throws Exception {
        when(service.create(anyString(), anyString())).thenThrow(new UsernameAlreadyExistsException("dup"));
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"dup\",\"initialPassword\":\"password123\"}"))
                .andExpect(status().isConflict());
    }

    @Test void createBlankUsernameReturns400() throws Exception {
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"initialPassword\":\"password123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void responseExcludesPasswordFields() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(eq("u"), anyString())).thenReturn(new UserResult(id, "u", "PENDING_ACTIVATION"));
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"u\",\"initialPassword\":\"password123\"}"))
                .andExpect(jsonPath("$.initialPassword").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.platformRole").doesNotExist())
                .andExpect(jsonPath("$.memberships").doesNotExist());
    }

    @Test void responseExcludesInternalFields() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(eq("u"), anyString())).thenReturn(new UserResult(id, "u", "PENDING_ACTIVATION"));
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"u\",\"initialPassword\":\"password123\"}"))
                .andExpect(jsonPath("$.platformRole").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.memberships").doesNotExist());
    }

    @Test void activateReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.activate(id)).thenReturn(new UserResult(id, "u", "NORMAL"));
        mvc.perform(post("/api/v1/users/" + id + "/activate"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NORMAL"));
    }
    @Test void activateNotFoundReturns404() throws Exception {
        when(service.activate(any())).thenThrow(new IllegalArgumentException("User not found"));
        mvc.perform(post("/api/v1/users/" + UUID.randomUUID() + "/activate")).andExpect(status().isNotFound());
    }
    @Test void activateStateConflictReturns409() throws Exception {
        when(service.activate(any())).thenThrow(new InvalidAccountStateTransitionException(AccountStatus.NORMAL, "activate"));
        mvc.perform(post("/api/v1/users/" + UUID.randomUUID() + "/activate")).andExpect(status().isConflict());
    }

    @Test void disableReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.disable(id)).thenReturn(new UserResult(id, "u", "DISABLED"));
        mvc.perform(post("/api/v1/users/" + id + "/disable"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));
    }
    @Test void disableNotFoundReturns404() throws Exception {
        when(service.disable(any())).thenThrow(new IllegalArgumentException("User not found"));
        mvc.perform(post("/api/v1/users/" + UUID.randomUUID() + "/disable")).andExpect(status().isNotFound());
    }

    @Test void reEnableReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.reEnable(id)).thenReturn(new UserResult(id, "u", "NORMAL"));
        mvc.perform(post("/api/v1/users/" + id + "/re-enable"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NORMAL"));
    }
    @Test void reEnableNotFoundReturns404() throws Exception {
        when(service.reEnable(any())).thenThrow(new IllegalArgumentException("User not found"));
        mvc.perform(post("/api/v1/users/" + UUID.randomUUID() + "/re-enable")).andExpect(status().isNotFound());
    }
}
