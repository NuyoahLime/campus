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
import java.net.HttpCookie;
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
        jdbc.update("DELETE FROM spring_session_attributes");
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM users");
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)",
                userId, username, encoder.encode(rawPassword), "NORMAL", "SUPER_ADMIN");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM spring_session_attributes");
        jdbc.update("DELETE FROM spring_session");
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

    // ── Phase 2: CSRF rotation ──

    private record CsrfData(String headerName, String token) {}

    private CsrfData fetchCsrf(HttpClient client) throws Exception {
        var resp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(200);
        var node = mapper.readTree(resp.body());
        return new CsrfData(node.get("headerName").asText(), node.get("token").asText());
    }

    private HttpResponse<String> login(HttpClient client, String user, String pass, CsrfData csrf) throws Exception {
        String body = "{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}";
        return client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header(csrf.headerName(), csrf.token())
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> logout(HttpClient client, CsrfData csrf) throws Exception {
        return client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/logout"))
                .header(csrf.headerName(), csrf.token())
                .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test void loginInvalidatesOldSessionAndCreatesNewAuthenticatedSession() throws Exception {
        // Step 1: deterministically create a real anonymous Spring Session in the DB.
        // Spring Session stores the raw UUID in primary_id/session_id columns.
        // The SESSION cookie value is Base64-encoded by DefaultCookieSerializer.
        String rawUuid = UUID.randomUUID().toString();
        String oldSessionCookie = java.util.Base64.getEncoder()
                .encodeToString(rawUuid.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        long expiry = now + 30 * 60 * 1000; // 30 min
        jdbc.update(
                "INSERT INTO spring_session(primary_id, session_id, creation_time, last_access_time, max_inactive_interval, expiry_time, principal_name) VALUES (?,?,?,?,?,?,?)",
                rawUuid, rawUuid, now, now, 1800, expiry, null);

        // Step 2: create a client carrying this anonymous session cookie
        var cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        cm.getCookieStore().add(URI.create(baseUrl()),
                new HttpCookie("SESSION", oldSessionCookie));
        var client = HttpClient.newBuilder().cookieHandler(cm).build();

        assertThat(oldSessionCookie)
                .as("Pre-login SESSION cookie must exist")
                .isNotNull().isNotEmpty();

        // Step 3: login — capture the Set-Cookie header directly
        CsrfData csrf = fetchCsrf(client);
        HttpResponse<String> loginResp = login(client, username, rawPassword, csrf);
        assertThat(loginResp.statusCode()).isEqualTo(200);

        // Step 4: login response must set a new SESSION cookie different from the old one
        String newSessionCookie = extractSessionFromSetCookie(loginResp);
        if (newSessionCookie == null) {
            newSessionCookie = extractSessionId(cm); // fallback to CookieManager
        }
        assertThat(newSessionCookie)
                .as("Successful login must set a new SESSION cookie")
                .isNotNull().isNotBlank();
        assertThat(newSessionCookie)
                .as("SESSION cookie must change after authentication")
                .isNotEqualTo(oldSessionCookie);

        // Step 5: new session can access /me
        var meResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(meResp.statusCode())
                .as("Authenticated session must be usable after login")
                .isEqualTo(200);

        // Step 6: old session cookie must be rejected.
        // Core fixation defense: the anonymous session must not become authenticated.
        var oldOnlyCm = new CookieManager();
        oldOnlyCm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        oldOnlyCm.getCookieStore().add(URI.create(baseUrl()),
                new HttpCookie("SESSION", oldSessionCookie));
        var oldOnlyClient = HttpClient.newBuilder().cookieHandler(oldOnlyCm).build();

        var oldMeResp = oldOnlyClient.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(oldMeResp.statusCode())
                .as("Old anonymous session must not become authenticated (session fixation defense)")
                .isEqualTo(401);
    }

    @Test void failedReloginPreservesOriginalIdentity() throws Exception {
        var client = newClient();

        // Step 1: login as user A
        CsrfData csrf = fetchCsrf(client);
        HttpResponse<String> loginResp = login(client, username, rawPassword, csrf);
        assertThat(loginResp.statusCode()).isEqualTo(200);

        // Step 2: /me returns user A
        var meResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(meResp.statusCode()).isEqualTo(200);
        assertThat(meResp.body()).contains(username);

        // Step 3: re-login with wrong password from same session
        CsrfData csrf2 = fetchCsrf(client);
        HttpResponse<String> failLogin = login(client, username, "wrongPassword1", csrf2);
        assertThat(failLogin.statusCode()).isEqualTo(401);

        // Step 4: /me still returns user A (identity preserved on failed re-login)
        var afterFailMe = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(afterFailMe.statusCode())
                .as("Failed re-login must preserve original authenticated identity")
                .isEqualTo(200);
        assertThat(afterFailMe.body()).contains(username);
    }

    private String extractSessionId(CookieManager cm) {
        for (HttpCookie cookie : cm.getCookieStore().getCookies()) {
            if ("SESSION".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String extractSessionFromSetCookie(HttpResponse<String> resp) {
        for (String header : resp.headers().allValues("Set-Cookie")) {
            if (header.startsWith("SESSION=") || header.startsWith("SESSION=")) {
                int eq = header.indexOf('=');
                int semi = header.indexOf(';');
                if (semi < 0) semi = header.length();
                if (eq > 0) return header.substring(eq + 1, semi);
            }
        }
        return null;
    }

    @Test void preLoginCsrfTokenIsInvalidAfterLogin() throws Exception {
        var client = newClient();

        CsrfData preLoginCsrf = fetchCsrf(client);

        HttpResponse<String> loginResp = login(client, username, rawPassword, preLoginCsrf);
        assertThat(loginResp.statusCode()).isEqualTo(200);

        // Old (pre-login) CSRF token must be rejected after session rotation
        HttpResponse<String> oldTokenLogout = logout(client, preLoginCsrf);
        assertThat(oldTokenLogout.statusCode())
                .as("Pre-login CSRF token must be invalid after login")
                .isEqualTo(403);

        // New CSRF token must work
        CsrfData newCsrf = fetchCsrf(client);
        assertThat(newCsrf.token())
                .as("CSRF token must be different after login")
                .isNotEqualTo(preLoginCsrf.token());

        HttpResponse<String> validLogout = logout(client, newCsrf);
        assertThat(validLogout.statusCode()).isEqualTo(204);
    }
}
