package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.exception.InvalidPasswordException;
import com.campusguinness.identity.application.exception.UsernameAlreadyExistsException;
import com.campusguinness.identity.application.service.UserApplicationService;
import com.campusguinness.interfaces.web.user.UserController;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthInputValidationHttpTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mvc;

    @MockitoBean
    private UserApplicationService userService;

    // ── Login validation ──

    @Test void loginWithEmptyUsernameReturns400() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"testPass123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void loginWithEmptyPasswordReturns400() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"testuser\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void loginWithUsernameOver100CharsReturns400() throws Exception {
        String longName = "x".repeat(101);
        mvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + longName + "\",\"password\":\"testPass123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void loginWithPasswordOver72Utf8BytesReturns400() throws Exception {
        String password = "a".repeat(69) + "😀"; // 73 UTF-8 bytes
        mvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"testuser\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test void loginErrorResponseDoesNotContainRawPassword() throws Exception {
        String rawPassword = "mySecret123";
        String resp = mvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"nonexistent\",\"password\":\"" + rawPassword + "\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(resp).doesNotContain(rawPassword);
    }

    // ── Validation error does not echo raw password ──

    @Test void validationErrorDoesNotContainRawPassword() throws Exception {
        String password = "a".repeat(69) + "😀";
        mvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"testuser\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString(password))));
    }

    // ── CreateUserRequest validation ──

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createUserWithPasswordOver72Utf8BytesReturns400() throws Exception {
        String password = "a".repeat(69) + "😀"; // 73 UTF-8 bytes
        mvc.perform(post("/api/v1/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"new-user\",\"initialPassword\":\"" + password + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ── Exception mapping ──

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void invalidPasswordExceptionMapsTo400() throws Exception {
        when(userService.create(anyString(), anyString()))
                .thenThrow(new InvalidPasswordException("PASSWORD_TOO_LONG"));

        String resp = mvc.perform(post("/api/v1/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"new-user\",\"initialPassword\":\"aaaaaaaa\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat(resp).contains("PASSWORD_TOO_LONG");
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void duplicateUsernameMapsTo409() throws Exception {
        when(userService.create(anyString(), anyString()))
                .thenThrow(new UsernameAlreadyExistsException("existing"));

        mvc.perform(post("/api/v1/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"existing\",\"initialPassword\":\"aaaaaaaa\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));
    }
}
