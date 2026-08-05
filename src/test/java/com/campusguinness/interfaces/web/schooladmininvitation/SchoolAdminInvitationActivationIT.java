package com.campusguinness.interfaces.web.schooladmininvitation;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.service.ActivateSchoolAdminCommand;
import com.campusguinness.identity.application.service.SchoolAdminActivationService;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "campus-guinness.security.cors.allowed-origins=http://localhost:5173")
class SchoolAdminInvitationActivationIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired SchoolAdminActivationService activationService;

    private final ObjectMapper mapper = new ObjectMapper();
    private final String runPrefix = "phase4-" + UUID.randomUUID().toString().substring(0, 8);
    private final String adminPassword = "AdminPass123!";
    private UUID superAdminId;
    private UUID ordinaryUserId;
    private UUID schoolId;
    private String superAdminUsername;
    private String ordinaryUsername;

    @BeforeEach
    void setUp() {
        superAdminId = UUID.randomUUID();
        ordinaryUserId = UUID.randomUUID();
        schoolId = UUID.randomUUID();
        superAdminUsername = username("super");
        ordinaryUsername = username("ordinary");
        insertUser(superAdminId, superAdminUsername, "NORMAL", "SUPER_ADMIN", adminPassword);
        insertUser(ordinaryUserId, ordinaryUsername, "NORMAL", null, adminPassword);
        insertSchool(schoolId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("""
                DELETE FROM school_memberships
                WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)
                """, runPrefix + "%");
        jdbc.update("""
                DELETE FROM school_admin_invitations
                WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)
                   OR created_by IN (SELECT id FROM users WHERE username LIKE ?)
                """, runPrefix + "%", runPrefix + "%");
        jdbc.update("DELETE FROM schools WHERE id = ?", schoolId);
        jdbc.update("DELETE FROM users WHERE username LIKE ?", runPrefix + "%");
    }

    @Test
    void superAdminCreatesInvitationAndAnonymousActivationCompletesAtomicChangesWithoutSession() throws Exception {
        var adminSession = login(superAdminUsername, adminPassword);
        String targetUsername = username("school-admin");

        var created = createInvitation(adminSession, targetUsername);
        assertThat(created.rawCode()).isNotBlank();
        assertThat(invitationCodeHash(created.invitationId())).isNotEqualTo(created.rawCode());
        assertThat(userStatus(created.userId())).isEqualTo("PENDING_ACTIVATION");
        assertThat(platformRole(created.userId())).isNull();
        assertThat(countMemberships(created.userId())).isZero();

        var csrf = csrfToken();
        mvc.perform(withCsrf(post("/api/v1/auth/school-admin/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activationBody(targetUsername, created.rawCode(), "NewPass123!")), csrf))
                .andExpect(status().isNoContent())
                .andExpect(cookie().doesNotExist("SESSION"));

        assertThat(userStatus(created.userId())).isEqualTo("NORMAL");
        assertThat(invitationStatus(created.invitationId())).isEqualTo("ACCEPTED");
        assertThat(acceptedAtPresent(created.invitationId())).isTrue();
        assertThat(countActiveSchoolAdminMemberships(created.userId(), schoolId)).isEqualTo(1);
        assertThat(encoder.matches("NewPass123!", passwordHash(created.userId()))).isTrue();

        mvc.perform(get("/api/v1/auth/me").cookie(csrf.cookies()))
                .andExpect(status().isUnauthorized());
        login(targetUsername, "NewPass123!");
    }

    @Test
    void wrongInvitationCodeAttemptsPersistAndRevokeAtLimit() throws Exception {
        var adminSession = login(superAdminUsername, adminPassword);
        var created = createInvitation(adminSession, username("wrong-code"));
        String originalPasswordHash = passwordHash(created.userId());
        var csrf = csrfToken();

        mvc.perform(withCsrf(post("/api/v1/auth/school-admin/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activationBody(created.username(), "wrong-code", "NewPass123!")), csrf))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVITATION_ACTIVATION_FAILED"));

        assertThat(failedAttempts(created.invitationId())).isEqualTo(1);
        assertThat(invitationStatus(created.invitationId())).isEqualTo("PENDING");

        for (int i = 0; i < 4; i++) {
            mvc.perform(withCsrf(post("/api/v1/auth/school-admin/activate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(activationBody(created.username(), "still-wrong", "NewPass123!")), csrf))
                    .andExpect(status().isUnauthorized());
        }

        assertThat(failedAttempts(created.invitationId())).isEqualTo(5);
        assertThat(invitationStatus(created.invitationId())).isEqualTo("REVOKED");
        assertThat(revokedAtPresent(created.invitationId())).isTrue();
        assertThat(userStatus(created.userId())).isEqualTo("PENDING_ACTIVATION");
        assertThat(passwordHash(created.userId())).isEqualTo(originalPasswordHash);
        assertThat(countMemberships(created.userId())).isZero();
    }

    @Test
    void csrfAndAuthorizationBoundariesAreEnforced() throws Exception {
        mvc.perform(withCsrf(post("/api/v1/school-admin-invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(username("anon"))), csrfToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        var ordinarySession = login(ordinaryUsername, adminPassword);
        mvc.perform(withCsrf(post("/api/v1/school-admin-invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(username("ordinary"))), ordinarySession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(post("/api/v1/auth/school-admin/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activationBody("missing", "code", "NewPass123!")))
                .andExpect(status().isForbidden());

        mvc.perform(withCsrf(post("/api/v1/auth/school-admin/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activationBody("missing", "code", "NewPass123!")), csrfToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVITATION_ACTIVATION_FAILED"));
    }

    @Test
    void concurrentActivationAllowsOnlyOneSuccess() throws Exception {
        String targetUsername = username("concurrent");
        UUID userId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        String rawCode = "concurrent-code-" + UUID.randomUUID();
        insertUser(userId, targetUsername, "PENDING_ACTIVATION", null, "Placeholder123!");
        insertInvitation(invitationId, userId, rawCode);

        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var successes = new AtomicInteger();
        var failures = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(2);
        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await(5, TimeUnit.SECONDS);
                    activationService.activate(new ActivateSchoolAdminCommand(
                            targetUsername, rawCode, "ConcurrentPass123!", "ConcurrentPass123!"));
                    successes.incrementAndGet();
                } catch (IdentityApplicationException ex) {
                    failures.incrementAndGet();
                } catch (Exception ex) {
                    failures.incrementAndGet();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(1);
        assertThat(userStatus(userId)).isEqualTo("NORMAL");
        assertThat(invitationStatus(invitationId)).isEqualTo("ACCEPTED");
        assertThat(countActiveSchoolAdminMemberships(userId, schoolId)).isEqualTo(1);
    }

    private InvitationCreated createInvitation(AuthSession session, String username) throws Exception {
        var result = mvc.perform(withCsrf(post("/api/v1/school-admin-invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(username)), session))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", matchesPattern(".*no-store.*")))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        return new InvitationCreated(
                UUID.fromString(body.get("userId").asText()),
                UUID.fromString(body.get("invitationId").asText()),
                body.get("username").asText(),
                body.get("invitationCode").asText()
        );
    }

    private AuthSession login(String username, String password) throws Exception {
        var csrf = csrfToken();
        var result = mvc.perform(withCsrf(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"), csrf))
                .andExpect(status().isOk())
                .andReturn();
        return new AuthSession(merge(csrf.cookies(), result.getResponse().getCookies()));
    }

    private CsrfMaterial csrfToken() throws Exception {
        var result = mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        return new CsrfMaterial(body.get("headerName").asText(), body.get("token").asText(),
                result.getResponse().getCookies());
    }

    private CsrfMaterial csrfToken(AuthSession session) throws Exception {
        var result = mvc.perform(get("/api/v1/auth/csrf").cookie(session.cookies()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        return new CsrfMaterial(body.get("headerName").asText(), body.get("token").asText(),
                merge(session.cookies(), result.getResponse().getCookies()));
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, AuthSession session)
            throws Exception {
        return withCsrf(request, csrfToken(session));
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, CsrfMaterial csrf) {
        return request.header(csrf.headerName(), csrf.token()).cookie(csrf.cookies());
    }

    private Cookie[] merge(Cookie[] left, Cookie[] right) {
        return List.of(left, right).stream()
                .flatMap(Arrays::stream)
                .toArray(Cookie[]::new);
    }

    private String createBody(String username) {
        return "{\"username\":\"" + username + "\",\"schoolId\":\"" + schoolId + "\"}";
    }

    private String activationBody(String username, String code, String password) {
        return "{\"username\":\"" + username + "\",\"invitationCode\":\"" + code
                + "\",\"newPassword\":\"" + password + "\",\"confirmPassword\":\"" + password + "\"}";
    }

    private void insertUser(UUID id, String username, String status, String platformRole, String rawPassword) {
        jdbc.update("INSERT INTO users(id, username, password_hash, account_status, platform_role) VALUES (?,?,?,?,?)",
                id, username, encoder.encode(rawPassword), status, platformRole);
    }

    private void insertSchool(UUID id) {
        jdbc.update("""
                INSERT INTO schools(
                    id, name, unified_code_type, unified_code, internal_code, school_type, region,
                    address, contact_name, contact_phone, contact_email, school_status
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id, runPrefix + "-school", "USCC", runPrefix + "-code", runPrefix + "-internal",
                "PRIMARY", "Beijing", "Address", "Contact", "13800000000",
                "phase4@example.com", "NORMAL");
    }

    private void insertInvitation(UUID invitationId, UUID userId, String rawCode) {
        jdbc.update("""
                INSERT INTO school_admin_invitations(
                    id, user_id, school_id, role_in_school, invitation_code_hash, invitation_status,
                    expires_at, created_by
                ) VALUES (?,?,?,?,?,?, now() + interval '1 day', ?)
                """,
                invitationId, userId, schoolId, "SCHOOL_ADMIN", encoder.encode(rawCode), "PENDING", superAdminId);
    }

    private String username(String label) {
        return runPrefix + "-" + label + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String userStatus(UUID userId) {
        return jdbc.queryForObject("SELECT account_status FROM users WHERE id = ?", String.class, userId);
    }

    private String platformRole(UUID userId) {
        return jdbc.queryForObject("SELECT platform_role FROM users WHERE id = ?", String.class, userId);
    }

    private String passwordHash(UUID userId) {
        return jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, userId);
    }

    private String invitationCodeHash(UUID invitationId) {
        return jdbc.queryForObject("SELECT invitation_code_hash FROM school_admin_invitations WHERE id = ?",
                String.class, invitationId);
    }

    private String invitationStatus(UUID invitationId) {
        return jdbc.queryForObject("SELECT invitation_status FROM school_admin_invitations WHERE id = ?",
                String.class, invitationId);
    }

    private int failedAttempts(UUID invitationId) {
        return jdbc.queryForObject("SELECT failed_attempts FROM school_admin_invitations WHERE id = ?",
                Integer.class, invitationId);
    }

    private boolean acceptedAtPresent(UUID invitationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT accepted_at IS NOT NULL FROM school_admin_invitations WHERE id = ?",
                Boolean.class, invitationId));
    }

    private boolean revokedAtPresent(UUID invitationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT revoked_at IS NOT NULL FROM school_admin_invitations WHERE id = ?",
                Boolean.class, invitationId));
    }

    private int countMemberships(UUID userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM school_memberships WHERE user_id = ?",
                Integer.class, userId);
    }

    private int countActiveSchoolAdminMemberships(UUID userId, UUID schoolId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM school_memberships
                WHERE user_id = ? AND school_id = ? AND role_in_school = 'SCHOOL_ADMIN' AND status = 'ACTIVE'
                """, Integer.class, userId, schoolId);
    }

    private record InvitationCreated(UUID userId, UUID invitationId, String username, String rawCode) {}
    private record AuthSession(Cookie[] cookies) {}
    private record CsrfMaterial(String headerName, String token, Cookie[] cookies) {}
}
