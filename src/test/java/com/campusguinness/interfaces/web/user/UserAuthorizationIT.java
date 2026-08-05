package com.campusguinness.interfaces.web.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "campus-guinness.security.cors.allowed-origins=http://localhost:5173")
class UserAuthorizationIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    private final ObjectMapper mapper = new ObjectMapper();
    private final String runPrefix = "authz-" + UUID.randomUUID().toString().substring(0, 8);
    private final String rawPassword = "testPass123";

    private UUID superAdminId;
    private UUID ordinaryUserId;
    private String superAdminUsername;
    private String ordinaryUsername;

    @BeforeEach
    void setUpUsers() {
        superAdminId = UUID.randomUUID();
        ordinaryUserId = UUID.randomUUID();
        superAdminUsername = username("super");
        ordinaryUsername = username("ordinary");

        insertUser(superAdminId, superAdminUsername, "NORMAL", "SUPER_ADMIN");
        insertUser(ordinaryUserId, ordinaryUsername, "NORMAL", null);
    }

    @AfterEach
    void cleanUpUsers() {
        jdbc.update("DELETE FROM users WHERE username LIKE ?", runPrefix + "%");
    }

    @Test
    void anonymousUserLifecycleRequestsReturn401() throws Exception {
        assertUnauthorized(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(username("anon"))), "/api/v1/users");
        assertUnauthorized(post("/api/v1/users/" + UUID.randomUUID() + "/activate"), "/api/v1/users/.+/activate");
        assertUnauthorized(post("/api/v1/users/" + UUID.randomUUID() + "/disable"), "/api/v1/users/.+/disable");
        assertUnauthorized(post("/api/v1/users/" + UUID.randomUUID() + "/re-enable"), "/api/v1/users/.+/re-enable");
    }

    @Test
    void ordinaryUserCreateReturns403WithValidCsrfAndNoDatabaseSideEffect() throws Exception {
        var login = login(ordinaryUsername);
        var before = countRunUsers();
        var rejectedUsername = username("rejected-create");

        mvc.perform(withCsrf(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(rejectedUsername)), login))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(countRunUsers()).isEqualTo(before);
        assertThat(countUsers(rejectedUsername)).isZero();
    }

    @Test
    void ordinaryUserActivateReturns403WithValidCsrfAndNoDatabaseSideEffect() throws Exception {
        var login = login(ordinaryUsername);
        var targetId = insertUser(username("pending-target"), "PENDING_ACTIVATION", null);

        mvc.perform(withCsrf(post("/api/v1/users/" + targetId + "/activate"), login))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(accountStatus(targetId)).isEqualTo("PENDING_ACTIVATION");
    }

    @Test
    void ordinaryUserDisableReturns403WithValidCsrfAndNoDatabaseSideEffect() throws Exception {
        var login = login(ordinaryUsername);
        var targetId = insertUser(username("normal-target"), "NORMAL", null);

        mvc.perform(withCsrf(post("/api/v1/users/" + targetId + "/disable"), login))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(accountStatus(targetId)).isEqualTo("NORMAL");
    }

    @Test
    void ordinaryUserReEnableReturns403WithValidCsrfAndNoDatabaseSideEffect() throws Exception {
        var login = login(ordinaryUsername);
        var targetId = insertUser(username("disabled-target"), "DISABLED", null);

        mvc.perform(withCsrf(post("/api/v1/users/" + targetId + "/re-enable"), login))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(accountStatus(targetId)).isEqualTo("DISABLED");
    }

    @Test
    void superAdminCreateWithValidCsrfCreatesPendingActivationUser() throws Exception {
        var login = login(superAdminUsername);
        var newUsername = username("created");

        mvc.perform(withCsrf(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(newUsername)), login))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(newUsername))
                .andExpect(jsonPath("$.status").value("PENDING_ACTIVATION"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(accountStatus(newUsername)).isEqualTo("PENDING_ACTIVATION");
    }

    @Test
    void superAdminActivateWithValidCsrfActivatesUser() throws Exception {
        var login = login(superAdminUsername);
        var targetId = insertUser(username("activate-target"), "PENDING_ACTIVATION", null);

        mvc.perform(withCsrf(post("/api/v1/users/" + targetId + "/activate"), login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NORMAL"));

        assertThat(accountStatus(targetId)).isEqualTo("NORMAL");
    }

    @Test
    void superAdminDisableWithValidCsrfDisablesUser() throws Exception {
        var login = login(superAdminUsername);
        var targetId = insertUser(username("disable-target"), "NORMAL", null);

        mvc.perform(withCsrf(post("/api/v1/users/" + targetId + "/disable"), login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        assertThat(accountStatus(targetId)).isEqualTo("DISABLED");
    }

    @Test
    void superAdminReEnableWithValidCsrfReEnablesUser() throws Exception {
        var login = login(superAdminUsername);
        var targetId = insertUser(username("reenable-target"), "DISABLED", null);

        mvc.perform(withCsrf(post("/api/v1/users/" + targetId + "/re-enable"), login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NORMAL"));

        assertThat(accountStatus(targetId)).isEqualTo("NORMAL");
    }

    @Test
    void superAdminCreateWithoutCsrfReturns403AndDoesNotCreateUser() throws Exception {
        var login = login(superAdminUsername);
        var before = countRunUsers();
        var rejectedUsername = username("missing-csrf");

        mvc.perform(post("/api/v1/users")
                        .cookie(login.cookies())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(rejectedUsername)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(countRunUsers()).isEqualTo(before);
        assertThat(countUsers(rejectedUsername)).isZero();
    }

    @Test
    void realLoginMeAndLogoutFlowStillWorksWithCsrf() throws Exception {
        var login = login(ordinaryUsername);

        mvc.perform(get("/api/v1/auth/me")
                        .cookie(login.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(ordinaryUsername));

        mvc.perform(withCsrf(post("/api/v1/auth/logout"), login))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    private void assertUnauthorized(MockHttpServletRequestBuilder request, String expectedPathPattern) throws Exception {
        mvc.perform(withCsrf(request, csrfToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.path").value(org.hamcrest.Matchers.matchesPattern(expectedPathPattern)));
    }

    private AuthSession login(String username) throws Exception {
        var csrf = csrfToken();
        var result = mvc.perform(withCsrf(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + rawPassword + "\"}"), csrf))
                .andExpect(status().isOk())
                .andReturn();

        return new AuthSession(merge(csrf.cookies(), result.getResponse().getCookies()));
    }

    private CsrfMaterial csrfToken() throws Exception {
        var result = mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        return new CsrfMaterial(body.get("headerName").asText(), body.get("token").asText(),
                result.getResponse().getCookies());
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, AuthSession session)
            throws Exception {
        return withCsrf(request, csrfToken(session));
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, CsrfMaterial csrf) {
        return request.header(csrf.headerName(), csrf.token()).cookie(csrf.cookies());
    }

    private CsrfMaterial csrfToken(AuthSession session) throws Exception {
        var result = mvc.perform(get("/api/v1/auth/csrf")
                        .cookie(session.cookies()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        return new CsrfMaterial(body.get("headerName").asText(), body.get("token").asText(),
                merge(session.cookies(), result.getResponse().getCookies()));
    }

    private Cookie[] merge(Cookie[] left, Cookie[] right) {
        return List.of(left, right).stream()
                .flatMap(Arrays::stream)
                .toArray(Cookie[]::new);
    }

    private String username(String label) {
        return runPrefix + "-" + label + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String createBody(String username) {
        return "{\"username\":\"" + username + "\",\"initialPassword\":\"" + rawPassword + "\"}";
    }

    private UUID insertUser(String username, String status, String platformRole) {
        var id = UUID.randomUUID();
        insertUser(id, username, status, platformRole);
        return id;
    }

    private void insertUser(UUID id, String username, String status, String platformRole) {
        jdbc.update("INSERT INTO users(id, username, password_hash, account_status, platform_role) VALUES (?,?,?,?,?)",
                id, username, encoder.encode(rawPassword), status, platformRole);
    }

    private String accountStatus(UUID id) {
        return jdbc.queryForObject("SELECT account_status FROM users WHERE id = ?", String.class, id);
    }

    private String accountStatus(String username) {
        return jdbc.queryForObject("SELECT account_status FROM users WHERE username = ?", String.class, username);
    }

    private int countRunUsers() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username LIKE ?", Integer.class, runPrefix + "%");
    }

    private int countUsers(String username) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, username);
    }

    private record AuthSession(Cookie[] cookies) {}

    private record CsrfMaterial(String headerName, String token, Cookie[] cookies) {}
}
