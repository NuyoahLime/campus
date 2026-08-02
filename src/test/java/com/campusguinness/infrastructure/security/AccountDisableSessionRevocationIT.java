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
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AccountDisableSessionRevocationIT {

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    private final ObjectMapper mapper = new ObjectMapper();

    private UUID adminId;
    private UUID normalUserId;
    private String adminName;
    private String normalName;
    private static final String RAW_PASSWORD = "testPass123";

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM spring_session_attributes");
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM school_memberships");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM schools");

        // Create school for memberships
        UUID schoolId = UUID.randomUUID();
        jdbc.update("INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status,created_at,updated_at,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,now(),now(),0)",
                schoolId, "测试学校", "USCC", "92440000REVOKE01", "SCH-REV", "MIDDLE_SCHOOL", "广州", "测试路", "联系人", "13800000000", "t@t.cn", "NORMAL");

        adminId = UUID.randomUUID();
        adminName = "rev-admin-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)",
                adminId, adminName, encoder.encode(RAW_PASSWORD), "NORMAL", "SUPER_ADMIN");

        normalUserId = UUID.randomUUID();
        normalName = "rev-normal-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)",
                normalUserId, normalName, encoder.encode(RAW_PASSWORD), "NORMAL", null);
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)",
                UUID.randomUUID(), normalUserId, schoolId, "STUDENT", "ACTIVE");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM spring_session_attributes");
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM school_memberships");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM schools");
    }

    private String baseUrl() { return "http://localhost:" + port; }

    private HttpClient newClient() {
        var cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        return HttpClient.newBuilder().cookieHandler(cm).build();
    }

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

    private HttpResponse<String> doPost(HttpClient client, String path, CsrfData csrf, String body) throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .header(csrf.headerName(), csrf.token())
                .POST(body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    // ── Tests ──

    @Test void disabledUserSessionIsRevoked() throws Exception {
        // Step 1: normal user login
        var normalClient = newClient();
        CsrfData normalCsrf = fetchCsrf(normalClient);
        HttpResponse<String> resp = login(normalClient, normalName, RAW_PASSWORD, normalCsrf);
        assertThat(resp.statusCode()).isEqualTo(200);

        // Step 2: verify normal user can access /me
        var meResp = normalClient.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(meResp.statusCode()).isEqualTo(200);

        // Step 3: admin login
        var adminClient = newClient();
        CsrfData adminCsrf = fetchCsrf(adminClient);
        login(adminClient, adminName, RAW_PASSWORD, adminCsrf);

        // Step 4: admin disables normal user
        CsrfData adminCsrf2 = fetchCsrf(adminClient);
        HttpResponse<String> disableResp = doPost(adminClient,
                "/api/v1/users/" + normalUserId + "/disable", adminCsrf2, null);
        assertThat(disableResp.statusCode()).isEqualTo(200);

        // Step 5: normal user's session must be revoked → 401
        var afterDisableMe = normalClient.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(afterDisableMe.statusCode())
                .as("Disabled user's session must be revoked")
                .isEqualTo(401);

        // Step 6: admin session still intact
        var adminMe = adminClient.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(adminMe.statusCode())
                .as("Admin session must be unaffected by disabling another user")
                .isEqualTo(200);
    }

    @Test void disabledUserCannotAccessMe() throws Exception {
        var normalClient = newClient();
        CsrfData csrf = fetchCsrf(normalClient);
        login(normalClient, normalName, RAW_PASSWORD, csrf);
        // Verify pre-disable access
        var before = normalClient.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(before.statusCode()).isEqualTo(200);

        // Admin disables
        var adminClient = newClient();
        CsrfData aCsrf = fetchCsrf(adminClient);
        login(adminClient, adminName, RAW_PASSWORD, aCsrf);
        CsrfData aCsrf2 = fetchCsrf(adminClient);
        doPost(adminClient, "/api/v1/users/" + normalUserId + "/disable", aCsrf2, null);

        // After disable, /me → 401
        var after = normalClient.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(after.statusCode()).isEqualTo(401);
    }

    @Test void disablingUserRevokesAllConcurrentSessions() throws Exception {
        var adminClient = newClient();
        CsrfData aCsrf = fetchCsrf(adminClient);
        login(adminClient, adminName, RAW_PASSWORD, aCsrf);

        // Normal user logs in from two independent clients
        var clientA = newClient();
        CsrfData csrfA = fetchCsrf(clientA);
        login(clientA, normalName, RAW_PASSWORD, csrfA);

        var clientB = newClient();
        CsrfData csrfB = fetchCsrf(clientB);
        login(clientB, normalName, RAW_PASSWORD, csrfB);

        // Both sessions work before disable
        var meA = clientA.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(meA.statusCode()).isEqualTo(200);

        var meB = clientB.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(meB.statusCode()).isEqualTo(200);

        // Verify two distinct sessions in DB
        Integer sessionCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM spring_session WHERE principal_name = ?",
                Integer.class, normalName);
        assertThat(sessionCount).as("Two concurrent sessions must exist").isEqualTo(2);

        // Admin disables the user
        CsrfData aCsrf2 = fetchCsrf(adminClient);
        HttpResponse<String> disableResp = doPost(adminClient,
                "/api/v1/users/" + normalUserId + "/disable", aCsrf2, null);
        assertThat(disableResp.statusCode()).isEqualTo(200);

        // Both client sessions must be revoked
        var afterA = clientA.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(afterA.statusCode())
                .as("Client A session must be revoked after disable").isEqualTo(401);

        var afterB = clientB.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(afterB.statusCode())
                .as("Client B session must be revoked after disable").isEqualTo(401);

        // Admin session still intact
        var adminMe = adminClient.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(adminMe.statusCode()).isEqualTo(200);
    }

    @Test void reEnableDoesNotRestoreOldSession() throws Exception {
        // Normal user login
        var normalClient = newClient();
        CsrfData csrf = fetchCsrf(normalClient);
        HttpResponse<String> loginResp = login(normalClient, normalName, RAW_PASSWORD, csrf);
        assertThat(loginResp.statusCode()).isEqualTo(200);

        String oldSessionCookie = extractSessionCookie(normalClient);
        assertThat(oldSessionCookie)
                .as("Initial authenticated SESSION must exist")
                .isNotNull().isNotBlank();

        // Admin disables then re-enables
        var adminClient = newClient();
        CsrfData aCsrf = fetchCsrf(adminClient);
        login(adminClient, adminName, RAW_PASSWORD, aCsrf);
        CsrfData aCsrf2 = fetchCsrf(adminClient);
        doPost(adminClient, "/api/v1/users/" + normalUserId + "/disable", aCsrf2, null);
        CsrfData aCsrf3 = fetchCsrf(adminClient);
        doPost(adminClient, "/api/v1/users/" + normalUserId + "/re-enable", aCsrf3, null);

        // Old session must still be invalid — unconditional
        var oldOnlyCm = new CookieManager();
        oldOnlyCm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        oldOnlyCm.getCookieStore().add(URI.create(baseUrl()),
                new HttpCookie("SESSION", oldSessionCookie));
        var oldClient = HttpClient.newBuilder().cookieHandler(oldOnlyCm).build();
        var oldMe = oldClient.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(oldMe.statusCode())
                .as("Re-enabled user must re-login; old sessions are not restored")
                .isEqualTo(401);

        // Re-login after re-enable must work
        var newClient = newClient();
        CsrfData newCsrf = fetchCsrf(newClient);
        HttpResponse<String> newLogin = login(newClient, normalName, RAW_PASSWORD, newCsrf);
        assertThat(newLogin.statusCode()).isEqualTo(200);
    }

    private String extractSessionCookie(HttpClient client) {
        return client.cookieHandler()
                .flatMap(h -> ((CookieManager) h).getCookieStore().getCookies().stream()
                        .filter(c -> "SESSION".equals(c.getName()))
                        .map(HttpCookie::getValue).findFirst())
                .orElseThrow(() -> new AssertionError("SESSION cookie must exist"));
    }

    @Test void requestTimeValidationRejectsDisabledAccountEvenWithValidSession() throws Exception {
        // Step 1: normal user login successfully
        var normalClient = newClient();
        CsrfData csrf = fetchCsrf(normalClient);
        login(normalClient, normalName, RAW_PASSWORD, csrf);

        var meResp = normalClient.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(meResp.statusCode()).isEqualTo(200);

        // Step 2: directly change account_status to DISABLED in DB
        // (simulates a TOCTOU race: disable happened after session creation,
        //  without session revocation being called at all)
        jdbc.update("UPDATE users SET account_status = 'DISABLED' WHERE id = ?", normalUserId);

        // Step 3: request-time validation must catch the disabled status.
        // This proves the filter is a fail-closed guard even when session
        // revocation never ran (TOCTOU race or partial failure scenario).
        var afterDisableMe = normalClient.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(afterDisableMe.statusCode())
                .as("Request-time filter must reject disabled account regardless of session state")
                .isEqualTo(401);

        // Verify the response contains the rejection error code
        assertThat(afterDisableMe.body()).contains("ACCOUNT_NOT_ACTIVE");
    }

    @Test void disableNonexistentUserDoesNotAffectOthers() throws Exception {
        // Admin login
        var adminClient = newClient();
        CsrfData csrf = fetchCsrf(adminClient);
        login(adminClient, adminName, RAW_PASSWORD, csrf);

        // Try disabling a random UUID
        CsrfData csrf2 = fetchCsrf(adminClient);
        HttpResponse<String> resp = doPost(adminClient,
                "/api/v1/users/" + UUID.randomUUID() + "/disable", csrf2, null);
        // Should fail (404 or 400), but admin session must remain
        assertThat(resp.statusCode()).isGreaterThanOrEqualTo(400);

        var adminMe = adminClient.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(adminMe.statusCode()).isEqualTo(200);
    }
}
