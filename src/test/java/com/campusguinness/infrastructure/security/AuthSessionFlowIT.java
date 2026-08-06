package com.campusguinness.infrastructure.security;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "campus-guinness.security.cors.allowed-origins=http://localhost:5173")
class AuthSessionFlowIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired SecurityContextRepository contextRepo;

    private UUID userId;
    private String username;
    private String rawPassword;

    @BeforeEach
    void setupTestUser() {
        userId = UUID.randomUUID();
        username = "authtest-" + UUID.randomUUID().toString().substring(0, 8);
        rawPassword = "testPass123";
        jdbc.update("DELETE FROM users");
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)",
                userId, username, encoder.encode(rawPassword), "NORMAL", "SUPER_ADMIN");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test void loginSuccessReturns200() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + rawPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.accountStatus").value("NORMAL"))
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_SUPER_ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test void loginWrongPasswordReturns401() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test void loginUnknownUserReturnsSame401() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"noSuchUser\",\"password\":\"pass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test void failedLoginDoesNotWriteSecurityContext() throws Exception {
        var session = new MockHttpSession();

        mvc.perform(post("/api/v1/auth/login")
                .session(session)
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
    }

    @Test void loginWithoutCsrfReturns403() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + rawPassword + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test void unauthenticatedMeReturns401() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void logoutWithoutCsrfReturns403() throws Exception {
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isForbidden());
    }

    @Test void securityFoundationRegression() throws Exception {
        mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/schools")).andExpect(status().isUnauthorized());
    }

    // CLAIM-01 evidence: SecurityContext is correctly saved to session
    @Test void contextRepoDirectSaveVerifiesCorrectBehavior() {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var user = new CampusGuinnessUserDetails(userId, username, encoder.encode(rawPassword), "NORMAL",
                java.util.Set.of(), java.util.List.of());
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user, null);
        var ctx = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);

        request.getSession(true);
        contextRepo.saveContext(ctx, request, response);

        var session = request.getSession(false);
        Assertions.assertNotNull(session);
        Assertions.assertNotNull(session.getAttribute("SPRING_SECURITY_CONTEXT"),
                "SPRING_SECURITY_CONTEXT must be saved to session by SecurityContextRepository");
    }
}
