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
        jdbc.update("DELETE FROM users");
        userId = UUID.randomUUID();
        username = "lockout-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)",
                userId, username, encoder.encode(RAW_PASSWORD), "NORMAL", "SUPER_ADMIN");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
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

    // ── Tests ──

    @Test void firstFourFailuresReturn401AndDoNotLock() throws Exception {
        var client = newClient();
        for (int i = 1; i <= 4; i++) {
            var resp = tryLogin(client, username, "wrongPass" + i);
            assertThat(resp.statusCode()).as("Attempt %d", i).isEqualTo(401);
        }

        // Account should not be locked yet
        Integer failures = jdbc.queryForObject(
                "SELECT login_failures FROM users WHERE id = ?", Integer.class, userId);
        assertThat(failures).isEqualTo(4);

        Instant locked = jdbc.queryForObject(
                "SELECT locked_until FROM users WHERE id = ?", Instant.class, userId);
        assertThat(locked).isNull();
    }

    @Test void fifthFailureLocksAccount() throws Exception {
        var client = newClient();
        for (int i = 1; i <= 5; i++) {
            tryLogin(client, username, "wrongPass" + i);
        }

        Integer failures = jdbc.queryForObject(
                "SELECT login_failures FROM users WHERE id = ?", Integer.class, userId);
        assertThat(failures).isEqualTo(5);

        Instant locked = jdbc.queryForObject(
                "SELECT locked_until FROM users WHERE id = ?", Instant.class, userId);
        assertThat(locked).isNotNull().isAfter(Instant.now());
    }

    @Test void correctPasswordDuringLockoutReturns401() throws Exception {
        // Trigger lockout: 5 failures
        var client = newClient();
        for (int i = 1; i <= 5; i++) {
            tryLogin(client, username, "wrongPass" + i);
        }

        // Try correct password while locked
        var resp = tryLogin(newClient(), username, RAW_PASSWORD);
        assertThat(resp.statusCode())
                .as("Correct password must be rejected while locked")
                .isEqualTo(401);
    }

    @Test void loginSuccessResetsFailureCounter() throws Exception {
        // 3 failures first
        var client = newClient();
        for (int i = 1; i <= 3; i++) {
            tryLogin(client, username, "wrongPass" + i);
        }

        // Then success
        var resp = tryLogin(newClient(), username, RAW_PASSWORD);
        assertThat(resp.statusCode()).isEqualTo(200);

        // Counter reset
        Integer failures = jdbc.queryForObject(
                "SELECT login_failures FROM users WHERE id = ?", Integer.class, userId);
        assertThat(failures).isEqualTo(0);

        Instant locked = jdbc.queryForObject(
                "SELECT locked_until FROM users WHERE id = ?", Instant.class, userId);
        assertThat(locked).isNull();
    }

    @Test void nonexistentUserReturnsSame401() throws Exception {
        var client = newClient();
        var resp = tryLogin(client, "nonexistent_user", "anyPassword");
        assertThat(resp.statusCode()).isEqualTo(401);
    }
}
