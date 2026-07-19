package com.campusguinness.infrastructure.security;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthSessionRuntimeIT {

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    private final ObjectMapper mapper = new ObjectMapper();
    private UUID userId;
    private String username;
    private String rawPassword;

    @BeforeEach
    void setupTestUser() {
        userId = UUID.randomUUID();
        username = "runtime-" + UUID.randomUUID().toString().substring(0, 8);
        rawPassword = "testPass123";
        jdbc.update("DELETE FROM users");
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)",
                userId, username, encoder.encode(rawPassword), "NORMAL", "SUPER_ADMIN");
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

    @Test void fullSessionFlowRealServlet() throws Exception {
        var client = newClient();

        // STEP 1: Get CSRF
        var csrfResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(csrfResp.statusCode()).isEqualTo(200);
        var csrfNode = mapper.readTree(csrfResp.body());
        String csrfToken = csrfNode.get("token").asText();
        String csrfHeaderName = csrfNode.get("headerName").asText(); // X-XSRF-TOKEN from CookieCsrfTokenRepository
        assertThat(csrfToken).isNotEmpty();

        // STEP 2: Login
        String loginBody = "{\"username\":\"" + username + "\",\"password\":\"" + rawPassword + "\"}";
        var loginResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header(csrfHeaderName, csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(loginBody)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(loginResp.statusCode()).isEqualTo(200);
        assertThat(loginResp.body()).contains(userId.toString());

        // STEP 3: GET /me — CookieJar sends all stored cookies
        var meResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(meResp.statusCode()).as("Session should be restored from cookie").isEqualTo(200);
        assertThat(meResp.body()).contains(userId.toString());

        // STEP 4: Get fresh CSRF for logout
        var csrf2Resp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String csrfToken2 = mapper.readTree(csrf2Resp.body()).get("token").asText();

        // STEP 5: Logout
        var logoutResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/logout"))
                .header(csrfHeaderName, csrfToken2)
                .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(logoutResp.statusCode()).isEqualTo(204);

        // STEP 6: /me after logout → 401
        var afterMeResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(afterMeResp.statusCode()).isEqualTo(401);
    }

    @Test void failedCsrfLogoutPreservesSession() throws Exception {
        var client = newClient();

        var csrfResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String csrfToken = mapper.readTree(csrfResp.body()).get("token").asText();
        String csrfHeader = mapper.readTree(csrfResp.body()).get("headerName").asText();

        client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header(csrfHeader, csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(
                    "{\"username\":\"" + username + "\",\"password\":\"" + rawPassword + "\"}")).build(),
                HttpResponse.BodyHandlers.ofString());

        // Logout WITHOUT CSRF → 403
        var logoutResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/logout"))
                .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(logoutResp.statusCode()).isEqualTo(403);

        // /me still works → 200
        var meResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(meResp.statusCode()).isEqualTo(200);
    }

    @Test void wrongPasswordNoSession() throws Exception {
        var client = newClient();

        var csrfResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String csrfToken = mapper.readTree(csrfResp.body()).get("token").asText();
        String csrfHeader = mapper.readTree(csrfResp.body()).get("headerName").asText();

        client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header(csrfHeader, csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(
                    "{\"username\":\"" + username + "\",\"password\":\"wrong\"}")).build(),
                HttpResponse.BodyHandlers.ofString());

        var meResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(meResp.statusCode()).isEqualTo(401);
    }
}
