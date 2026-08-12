package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.result.UserResult;
import com.campusguinness.identity.application.service.UserApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "campus-guinness.security.cors.allowed-origins=http://localhost:5173")
class SecurityFoundationTest {

    @Autowired MockMvc mvc;
    @MockitoBean UserApplicationService users;

    // ── Public endpoints ──

    @Test void healthPermitted() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test void infoPermitted() throws Exception {
        mvc.perform(get("/actuator/info")).andExpect(status().isOk());
    }

    @Test void csrfEndpointReturnsToken() throws Exception {
        mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").isNotEmpty())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    // ── Unauthenticated → 401 JSON ──

    @Test void unauthenticatedReturns401Json() throws Exception {
        mvc.perform(get("/api/v1/auth/csrf").secure(true))
                .andExpect(status().isOk()); // CSRF endpoint is public

        mvc.perform(get("/api/v1/schools/" + java.util.UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test void unauthenticatedNo302() throws Exception {
        mvc.perform(get("/api/v1/schools/" + java.util.UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test void schoolListIsAnonymousPublicContract() throws Exception {
        mvc.perform(get("/api/v1/schools"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"));
    }

    // ── CSRF protection ──

    @Test @WithMockUser(roles = "SUPER_ADMIN")
    void postWithoutCsrfReturns403() throws Exception {
        mvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"testuser\",\"initialPassword\":\"password123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test @WithMockUser(roles = "SUPER_ADMIN")
    void postWithCsrfReachesController() throws Exception {
        when(users.create(eq("testuser"), eq("password123")))
                .thenReturn(new UserResult(UUID.randomUUID(), "testuser", "PENDING_ACTIVATION"));

        mvc.perform(post("/api/v1/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"testuser\",\"initialPassword\":\"password123\"}"))
                .andExpect(status().isCreated());
    }

    // ── CORS preflight ──

    @Test void allowedOriginPreflightReturnsOk() throws Exception {
        mvc.perform(options("/api/v1/schools")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk());
    }

    // ── Bean presence ──

    @Autowired(required = false) org.springframework.security.web.SecurityFilterChain chain;
    @Autowired(required = false) CampusAuthenticationProvider provider;
    @Autowired(required = false) org.springframework.security.authentication.AuthenticationManager authManager;

    @Test void securityFilterChainExists() { assert chain != null; }
    @Test void campusAuthProviderExists() { assert provider != null; }
    @Test void authManagerExists() { assert authManager != null; }
}
