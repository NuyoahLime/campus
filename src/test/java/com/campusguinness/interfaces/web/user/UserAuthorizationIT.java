package com.campusguinness.interfaces.web.user;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Authorization integration tests for /api/v1/users/** — real Spring Security filters.
 * <p>
 * Verifies:
 * <ul>
 *   <li>Anonymous → 401</li>
 *   <li>Normal user → 403</li>
 *   <li>SUPER_ADMIN → 201/200 (success)</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserAuthorizationIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    private UUID adminId;
    private UUID normalUserId;
    private String adminName;
    private String normalName;
    private UUID schoolId;
    private static final String RAW_PASSWORD = "testPass123";

    @BeforeEach
    void setupUsers() {
        jdbc.update("DELETE FROM school_memberships");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM schools");

        // Create a school for the normal user's membership
        schoolId = UUID.randomUUID();
        jdbc.update("INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status,created_at,updated_at,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,now(),now(),0)",
                schoolId, "测试学校", "USCC", "92440000AUTO0001", "SCH-AUTO", "MIDDLE_SCHOOL", "广东广州", "测试路1号", "联系人", "13800000000", "test@test.cn", "NORMAL");

        adminId = UUID.randomUUID();
        adminName = "ua-admin-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)",
                adminId, adminName, encoder.encode(RAW_PASSWORD), "NORMAL", "SUPER_ADMIN");

        normalUserId = UUID.randomUUID();
        normalName = "ua-normal-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)",
                normalUserId, normalName, encoder.encode(RAW_PASSWORD), "NORMAL", null);

        // Normal user needs a school membership to log in (identity resolver requires it)
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)",
                UUID.randomUUID(), normalUserId, schoolId, "STUDENT", "ACTIVE");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM school_memberships");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM schools");
    }

    // ── Anonymous (401) ──

    @Test
    void anonymousCreateUserReturns401() throws Exception {
        mvc.perform(post("/api/v1/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"anonuser\",\"initialPassword\":\"testPass123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousActivateReturns401() throws Exception {
        mvc.perform(post("/api/v1/users/" + UUID.randomUUID() + "/activate")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousDisableReturns401() throws Exception {
        mvc.perform(post("/api/v1/users/" + UUID.randomUUID() + "/disable")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousReEnableReturns401() throws Exception {
        mvc.perform(post("/api/v1/users/" + UUID.randomUUID() + "/re-enable")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ── Normal user (403) ──

    @Test
    void normalUserCreateUserReturns403() throws Exception {
        // Login as normal user first
        var loginResult = mvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + normalName + "\",\"password\":\"" + RAW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        // Use the session from login
        var sessionCookie = loginResult.getResponse().getCookies();
        var req = post("/api/v1/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newuser\",\"initialPassword\":\"testPass123\"}");
        if (sessionCookie != null && sessionCookie.length > 0) {
            req.cookie(sessionCookie);
        }
        mvc.perform(req).andExpect(status().isForbidden());
    }

    @Test
    void normalUserActivateReturns403() throws Exception {
        var loginResult = mvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + normalName + "\",\"password\":\"" + RAW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        var cookies = loginResult.getResponse().getCookies();
        var req = post("/api/v1/users/" + UUID.randomUUID() + "/activate").with(csrf());
        if (cookies != null) for (var c : cookies) req.cookie(c);
        mvc.perform(req).andExpect(status().isForbidden());
    }

    @Test
    void normalUserDisableReturns403() throws Exception {
        var loginResult = mvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + normalName + "\",\"password\":\"" + RAW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        var cookies = loginResult.getResponse().getCookies();
        var req = post("/api/v1/users/" + UUID.randomUUID() + "/disable").with(csrf());
        if (cookies != null) for (var c : cookies) req.cookie(c);
        mvc.perform(req).andExpect(status().isForbidden());
    }

    @Test
    void normalUserReEnableReturns403() throws Exception {
        var loginResult = mvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + normalName + "\",\"password\":\"" + RAW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        var cookies = loginResult.getResponse().getCookies();
        var req = post("/api/v1/users/" + UUID.randomUUID() + "/re-enable").with(csrf());
        if (cookies != null) for (var c : cookies) req.cookie(c);
        mvc.perform(req).andExpect(status().isForbidden());
    }

    // ── SUPER_ADMIN (success) ──

    @Test
    void superAdminCreateUserReturns201() throws Exception {
        var loginResult = mvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + adminName + "\",\"password\":\"" + RAW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        var cookies = loginResult.getResponse().getCookies();
        String newUsername = "sa-created-" + UUID.randomUUID().toString().substring(0, 8);
        var req = post("/api/v1/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + newUsername + "\",\"initialPassword\":\"testPass123\"}");
        if (cookies != null) for (var c : cookies) req.cookie(c);
        mvc.perform(req)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(newUsername))
                .andExpect(jsonPath("$.status").value("PENDING_ACTIVATION"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void superAdminActivateUserReturns200() throws Exception {
        // Create a user to activate
        UUID targetId = UUID.randomUUID();
        String targetName = "sa-target-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)",
                targetId, targetName, encoder.encode(RAW_PASSWORD), "PENDING_ACTIVATION", null);

        var loginResult = mvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + adminName + "\",\"password\":\"" + RAW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        var cookies = loginResult.getResponse().getCookies();
        var req = post("/api/v1/users/" + targetId + "/activate").with(csrf());
        if (cookies != null) for (var c : cookies) req.cookie(c);
        mvc.perform(req)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NORMAL"));
    }

    @Test
    void superAdminDisableUserReturns200() throws Exception {
        UUID targetId = UUID.randomUUID();
        String targetName = "sa-disable-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)",
                targetId, targetName, encoder.encode(RAW_PASSWORD), "NORMAL", null);

        var loginResult = mvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + adminName + "\",\"password\":\"" + RAW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        var cookies = loginResult.getResponse().getCookies();
        var req = post("/api/v1/users/" + targetId + "/disable").with(csrf());
        if (cookies != null) for (var c : cookies) req.cookie(c);
        mvc.perform(req)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));
    }

    @Test
    void superAdminReEnableUserReturns200() throws Exception {
        UUID targetId = UUID.randomUUID();
        String targetName = "sa-reen-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)",
                targetId, targetName, encoder.encode(RAW_PASSWORD), "DISABLED", null);

        var loginResult = mvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + adminName + "\",\"password\":\"" + RAW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        var cookies = loginResult.getResponse().getCookies();
        var req = post("/api/v1/users/" + targetId + "/re-enable").with(csrf());
        if (cookies != null) for (var c : cookies) req.cookie(c);
        mvc.perform(req)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NORMAL"));
    }

    // ── Normal user can still access /auth/me (regression check) ──

    @Test
    void normalUserCanStillAccessOwnProfile() throws Exception {
        var loginResult = mvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + normalName + "\",\"password\":\"" + RAW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        var cookies = loginResult.getResponse().getCookies();
        var req = get("/api/v1/auth/me");
        if (cookies != null) for (var c : cookies) req.cookie(c);
        mvc.perform(req)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(normalName));
    }
}
