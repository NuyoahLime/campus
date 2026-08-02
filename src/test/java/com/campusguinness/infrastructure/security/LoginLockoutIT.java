package com.campusguinness.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LoginLockoutIT {

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    private final ObjectMapper mapper = new ObjectMapper();

    private UUID userId;
    private String username;
    private static final String RAW_PASSWORD = "testPass123";

    @BeforeEach
    void setup() {
        userId = UUID.randomUUID();
        username = "lockout-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)",
                userId, username, encoder.encode(RAW_PASSWORD), "NORMAL", "SUPER_ADMIN");
    }

    @AfterEach
    void cleanup() {
        if (username != null) {
            jdbc.update("DELETE FROM spring_session WHERE principal_name = ?", username);
        }
        if (userId != null) {
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    private String baseUrl() { return "http://localhost:" + port; }

    private HttpClient newClient() {
        var cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        return HttpClient.newBuilder().cookieHandler(cm).build();
    }

    private String fetchCsrfToken(HttpClient client) throws Exception {
        var resp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(200);
        return mapper.readTree(resp.body()).get("token").asText();
    }

    private HttpResponse<String> tryLogin(HttpClient client, String user, String pass) throws Exception {
        String csrf = fetchCsrfToken(client);
        String body = "{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}";
        return client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    // ── Existing tests ──

    @Test void firstFourFailuresReturn401AndDoNotLock() throws Exception {
        var client = newClient();
        for (int i = 1; i <= 4; i++) {
            var resp = tryLogin(client, username, "wrongPass" + i);
            assertThat(resp.statusCode()).as("Attempt %d", i).isEqualTo(401);
        }
        Integer failures = jdbc.queryForObject("SELECT login_failures FROM users WHERE id = ?", Integer.class, userId);
        assertThat(failures).isEqualTo(4);
        Instant locked = jdbc.queryForObject("SELECT locked_until FROM users WHERE id = ?", Instant.class, userId);
        assertThat(locked).isNull();
    }

    @Test void fifthFailureLocksAccount() throws Exception {
        var client = newClient();
        for (int i = 1; i <= 5; i++) {
            tryLogin(client, username, "wrongPass" + i);
        }
        Integer failures = jdbc.queryForObject("SELECT login_failures FROM users WHERE id = ?", Integer.class, userId);
        assertThat(failures).isEqualTo(5);
        Instant locked = jdbc.queryForObject("SELECT locked_until FROM users WHERE id = ?", Instant.class, userId);
        assertThat(locked).isNotNull().isAfter(Instant.now());
    }

    @Test void correctPasswordDuringLockoutReturns401() throws Exception {
        var client = newClient();
        for (int i = 1; i <= 5; i++) { tryLogin(client, username, "wrongPass" + i); }
        var resp = tryLogin(newClient(), username, RAW_PASSWORD);
        assertThat(resp.statusCode()).isEqualTo(401);
    }

    @Test void loginSuccessResetsFailureCounter() throws Exception {
        var client = newClient();
        for (int i = 1; i <= 3; i++) { tryLogin(client, username, "wrongPass" + i); }
        var resp = tryLogin(newClient(), username, RAW_PASSWORD);
        assertThat(resp.statusCode()).isEqualTo(200);
        Integer failures = jdbc.queryForObject("SELECT login_failures FROM users WHERE id = ?", Integer.class, userId);
        assertThat(failures).isEqualTo(0);
    }

    // ── New boundary tests ──

    @Test void attemptDuringActiveLockoutDoesNotExtendLock() throws Exception {
        var client = newClient();
        for (int i = 1; i <= 5; i++) { tryLogin(client, username, "wrongPass" + i); }

        Integer failuresBefore = jdbc.queryForObject("SELECT login_failures FROM users WHERE id = ?", Integer.class, userId);
        Instant lockedBefore = jdbc.queryForObject("SELECT locked_until FROM users WHERE id = ?", Instant.class, userId);

        var resp = tryLogin(newClient(), username, "anotherWrong");
        assertThat(resp.statusCode()).isEqualTo(401);

        Integer failuresAfter = jdbc.queryForObject("SELECT login_failures FROM users WHERE id = ?", Integer.class, userId);
        Instant lockedAfter = jdbc.queryForObject("SELECT locked_until FROM users WHERE id = ?", Instant.class, userId);

        assertThat(failuresBefore).isEqualTo(5);
        assertThat(failuresAfter).isEqualTo(5);
        assertThat(lockedAfter).isEqualTo(lockedBefore);
    }

    @Test void firstFailureAfterLockExpiryStartsNewWindow() throws Exception {
        jdbc.update("UPDATE users SET login_failures = 5, locked_until = now() - INTERVAL '1 minute' WHERE id = ?", userId);
        var resp = tryLogin(newClient(), username, "wrongAfterExpiry");
        assertThat(resp.statusCode()).isEqualTo(401);

        Integer failures = jdbc.queryForObject("SELECT login_failures FROM users WHERE id = ?", Integer.class, userId);
        Instant lockedUntil = jdbc.queryForObject("SELECT locked_until FROM users WHERE id = ?", Instant.class, userId);
        assertThat(failures).isEqualTo(1);
        assertThat(lockedUntil).isNull();
    }

    @Test void disabledAccountAttemptDoesNotChangeFailureState() throws Exception {
        jdbc.update("UPDATE users SET account_status = 'DISABLED' WHERE id = ?", userId);
        var resp = tryLogin(newClient(), username, "wrongPassword");
        assertThat(resp.statusCode()).isEqualTo(401);

        Integer failures = jdbc.queryForObject("SELECT login_failures FROM users WHERE id = ?", Integer.class, userId);
        Instant lockedUntil = jdbc.queryForObject("SELECT locked_until FROM users WHERE id = ?", Instant.class, userId);
        assertThat(failures).isZero();
        assertThat(lockedUntil).isNull();
    }

    @Test void pendingActivationAccountDoesNotChangeFailureState() throws Exception {
        jdbc.update("UPDATE users SET account_status = 'PENDING_ACTIVATION' WHERE id = ?", userId);
        var resp = tryLogin(newClient(), username, "wrongPassword");
        assertThat(resp.statusCode()).isEqualTo(401);
        Integer failures = jdbc.queryForObject("SELECT login_failures FROM users WHERE id = ?", Integer.class, userId);
        assertThat(failures).isZero();
    }

    @Test void domainLockedAccountDoesNotChangeFailureState() throws Exception {
        jdbc.update("UPDATE users SET account_status = 'LOCKED' WHERE id = ?", userId);
        var resp = tryLogin(newClient(), username, "wrongPassword");
        assertThat(resp.statusCode()).isEqualTo(401);
        Integer failures = jdbc.queryForObject("SELECT login_failures FROM users WHERE id = ?", Integer.class, userId);
        assertThat(failures).isZero();
    }

    @Test void nonexistentUserReturnsSameResponseAsBadPassword() throws Exception {
        var knownResp = tryLogin(newClient(), username, "wrongPassword");
        var unknownResp = tryLogin(newClient(), "nonexistent_user_" + UUID.randomUUID(), "wrongPassword");
        assertThat(knownResp.statusCode()).isEqualTo(401);
        assertThat(unknownResp.statusCode()).isEqualTo(401);
        // Both must return the same error code and message (timestamps may differ)
        assertThat(unknownResp.body()).contains("AUTHENTICATION_FAILED");
        assertThat(unknownResp.body()).contains("The username or password is invalid");
        Integer failures = jdbc.queryForObject("SELECT login_failures FROM users WHERE id = ?", Integer.class, userId);
        assertThat(failures).isEqualTo(1);
    }
}
