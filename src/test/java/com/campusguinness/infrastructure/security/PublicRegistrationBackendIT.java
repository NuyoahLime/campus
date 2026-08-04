package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.port.MailDeliveryPort;
import com.campusguinness.identity.application.port.MailMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "campus-guinness.security.cors.allowed-origins=http://localhost:5173",
        "app.mail.public-frontend-url=https://app.example.com"
})
class PublicRegistrationBackendIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoBean MailDeliveryPort mailDeliveryPort;

    @AfterEach
    void tearDown() {
        jdbc.update("""
                DELETE FROM school_memberships
                WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'phase2-%')
                """);
        jdbc.update("DELETE FROM users WHERE username LIKE 'phase2-%'");
        reset(mailDeliveryPort);
    }

    @Test
    void anonymousCanRegisterWithCsrf() throws Exception {
        String username = unique("phase2-register");
        mvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, "User@Example.com", "Example123!", "Example123!")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.verificationRequired").value(true))
                .andExpect(jsonPath("$.nextAction").value("VERIFY_EMAIL"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.platformRole").doesNotExist())
                .andExpect(jsonPath("$.accountStatus").doesNotExist());

        assertRegisteredUserPersisted(username, "user@example.com");
        verify(mailDeliveryPort).send(any(MailMessage.class));
    }

    @Test
    void registerWithoutCsrfReturns403() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(unique("phase2-nocsrf"), "nocsrf@example.com", "Example123!", "Example123!")))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrationHashesPasswordAndStoresOnlyTokenHash() throws Exception {
        String username = unique("phase2-hash");
        String password = "Example123!";
        mvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, "hash@example.com", password, password)))
                .andExpect(status().isCreated());

        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = ?", String.class, username);
        assertThat(hash).isNotEqualTo(password);
        assertThat(passwordEncoder.matches(password, hash)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM school_memberships sm JOIN users u ON u.id=sm.user_id WHERE u.username=?",
                Integer.class, username)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE username=? AND activation_issued_at IS NULL AND activation_expires_at IS NULL",
                Integer.class, username)).isOne();

        String rawToken = lastRawToken();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_verification_tokens WHERE token_hash = ?",
                Integer.class, rawToken)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_verification_tokens evt JOIN users u ON u.id=evt.user_id WHERE u.username=? AND length(evt.token_hash)=64",
                Integer.class, username)).isOne();
    }

    @Test
    void roleInjectionCannotEscalate() throws Exception {
        String username = unique("phase2-inject");
        mvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "email":"inject@example.com",
                                  "password":"Example123!",
                                  "confirmPassword":"Example123!",
                                  "role":"SUPER_ADMIN",
                                  "platformRole":"SUPER_ADMIN",
                                  "accountStatus":"LOCKED",
                                  "schoolId":"%s"
                                }
                                """.formatted(username, UUID.randomUUID())))
                .andExpect(status().isCreated());

        var row = jdbc.queryForMap(
                "SELECT account_status, platform_role, registration_source FROM users WHERE username=?",
                username);
        assertThat(row.get("account_status")).isEqualTo("NORMAL");
        assertThat(row.get("platform_role")).isEqualTo("REGISTERED_USER");
        assertThat(row.get("registration_source")).isEqualTo("PUBLIC");
    }

    @Test
    void weakPasswordAndMismatchRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(unique("phase2-weak"), "weak@example.com", "short", "short")))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(unique("phase2-mismatch"), "mismatch@example.com", "Example123!", "Different123!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));
    }

    @Test
    void duplicateUsernameAndEmailReturnGenericConflict() throws Exception {
        String username = unique("phase2-dupe");
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, "dupe@example.com", "Example123!", "Example123!")))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, "other@example.com", "Example123!", "Example123!")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGISTRATION_UNAVAILABLE"));

        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(unique("phase2-dupe"), "dupe@example.com", "Example123!", "Example123!")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGISTRATION_UNAVAILABLE"));
    }

    @Test
    void mailFailureDoesNotRollbackRegistration() throws Exception {
        doThrow(new IllegalStateException("smtp unavailable")).when(mailDeliveryPort).send(any(MailMessage.class));
        String username = unique("phase2-mailfail");

        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, "mailfail@example.com", "Example123!", "Example123!")))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE username=?", Integer.class, username)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM email_verification_tokens evt
                JOIN users u ON u.id = evt.user_id
                WHERE u.username = ?
                """, Integer.class, username)).isOne();
    }

    @Test
    void validVerificationMarksEmailVerifiedTokenUsedAndInvalidatesOtherTokens() throws Exception {
        String username = unique("phase2-verify");
        register(username, "verify@example.com");
        String oldToken = lastRawToken();
        mvc.perform(post("/api/v1/auth/resend-verification").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"verify@example.com\"}"))
                .andExpect(status().isAccepted());
        String newToken = lastRawToken();

        mvc.perform(post("/api/v1/auth/verify-email").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + newToken + "\"}"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));

        assertThat(jdbc.queryForObject("SELECT email_verified_at IS NOT NULL FROM users WHERE username=?",
                Boolean.class, username)).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM email_verification_tokens evt
                JOIN users u ON u.id = evt.user_id
                WHERE u.username = ? AND evt.used_at IS NOT NULL
                """, Integer.class, username)).isEqualTo(2);

        mvc.perform(post("/api/v1/auth/verify-email").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + oldToken + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID"));
    }

    @Test
    void invalidVerificationCasesReturnGenericError() throws Exception {
        mvc.perform(post("/api/v1/auth/verify-email").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"not-a-real-token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID"));
    }

    @Test
    void resendAlwaysReturnsGeneric202AndOnlyEligibleUserReceivesToken() throws Exception {
        mvc.perform(post("/api/v1/auth/resend-verification").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unknown@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("If an unverified account exists, a verification email will be sent."));
        verify(mailDeliveryPort, never()).send(any(MailMessage.class));

        String username = unique("phase2-resend");
        register(username, "resend@example.com");
        int before = tokenCount(username);

        mvc.perform(post("/api/v1/auth/resend-verification").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"resend@example.com\"}"))
                .andExpect(status().isAccepted());

        assertThat(tokenCount(username)).isEqualTo(before + 1);
        assertThat(activeTokenCount(username)).isOne();
    }

    @Test
    void resendIsRateLimitedWithoutEnumeration() throws Exception {
        String username = unique("phase2-limit");
        String email = username + "@example.com";
        register(username, email);
        reset(mailDeliveryPort);

        for (int i = 0; i < 6; i++) {
            mvc.perform(post("/api/v1/auth/resend-verification")
                            .with(csrf())
                            .header("X-Forwarded-For", "203.0.113.44")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\"}"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.message").value("If an unverified account exists, a verification email will be sent."));
        }

        verify(mailDeliveryPort, times(5)).send(any(MailMessage.class));
    }

    @Test
    void unverifiedPublicUserCannotCreateSession() throws Exception {
        String username = unique("phase2-unverified");
        register(username, "unverified@example.com");

        var result = mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, "Example123!")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_REQUIRED"))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void verifiedRegisteredUserCanLoginRestoreSessionAndLogout() throws Exception {
        String username = unique("phase2-login");
        register(username, "login@example.com");
        verifyEmail(lastRawToken());

        var login = mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, "Example123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("NORMAL"))
                .andExpect(jsonPath("$.platformRole").value("REGISTERED_USER"))
                .andExpect(jsonPath("$.primaryRole").value("REGISTERED_USER"))
                .andExpect(jsonPath("$.primarySchoolId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.roles[0]").value("REGISTERED_USER"))
                .andExpect(jsonPath("$.schoolMemberships").isEmpty())
                .andExpect(jsonPath("$.onboardingRequired").value(true))
                .andReturn();

        var sessionCookie = login.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();
        mvc.perform(get("/api/v1/auth/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryRole").value("REGISTERED_USER"));

        mvc.perform(post("/api/v1/auth/logout").cookie(sessionCookie).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void registeredUserWithMembershipIsAmbiguous() throws Exception {
        String username = unique("phase2-ambiguous");
        UUID userId = insertVerifiedRegisteredUser(username, "ambiguous@example.com");
        UUID schoolId = insertSchool();
        jdbc.update("""
                INSERT INTO school_memberships(id, user_id, school_id, role_in_school, status)
                VALUES (?, ?, ?, 'STUDENT', 'ACTIVE')
                """, UUID.randomUUID(), userId, schoolId);

        mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, "Example123!")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_AMBIGUOUS"));
    }

    @Test
    void adminProvisionedUserWithoutEmailStillWorks() throws Exception {
        String username = unique("phase2-admin");
        jdbc.update("""
                INSERT INTO users(id, username, password_hash, account_status, platform_role, registration_source)
                VALUES (?, ?, ?, 'NORMAL', 'SUPER_ADMIN', 'ADMIN_PROVISIONED')
                """, UUID.randomUUID(), username, passwordEncoder.encode("Example123!"));

        mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, "Example123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryRole").value("SUPER_ADMIN"));
    }

    @Test
    void registeredUserApiIsolation() throws Exception {
        String username = unique("phase2-api");
        register(username, "api@example.com");
        verifyEmail(lastRawToken());
        var login = mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, "Example123!")))
                .andExpect(status().isOk())
                .andReturn();
        var sessionCookie = login.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();

        mvc.perform(get("/api/v1/schools").cookie(sessionCookie))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/onboarding").cookie(sessionCookie))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/student/scores").cookie(sessionCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REGISTERED_USER_ONBOARDING_REQUIRED"));
        mvc.perform(get("/api/v1/admin/operations").cookie(sessionCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REGISTERED_USER_ONBOARDING_REQUIRED"));
    }

    private void register(String username, String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, email, "Example123!", "Example123!")))
                .andExpect(status().isCreated());
    }

    private void verifyEmail(String token) throws Exception {
        mvc.perform(post("/api/v1/auth/verify-email").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isNoContent());
    }

    private String lastRawToken() {
        var captor = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailDeliveryPort, atLeastOnce()).send(captor.capture());
        String body = captor.getAllValues().getLast().textBody();
        var matcher = Pattern.compile("token=([^\\s]+)").matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private void assertRegisteredUserPersisted(String username, String email) {
        var row = jdbc.queryForMap("""
                SELECT username, email, email_normalized, email_verified_at, account_status,
                    platform_role, registration_source, login_failures,
                    activation_issued_at, activation_expires_at
                FROM users
                WHERE username = ?
                """, username);
        assertThat(row.get("username")).isEqualTo(username);
        assertThat(row.get("email")).isEqualTo(email);
        assertThat(row.get("email_normalized")).isEqualTo(email);
        assertThat(row.get("email_verified_at")).isNull();
        assertThat(row.get("account_status")).isEqualTo("NORMAL");
        assertThat(row.get("platform_role")).isEqualTo("REGISTERED_USER");
        assertThat(row.get("registration_source")).isEqualTo("PUBLIC");
        assertThat(row.get("login_failures")).isEqualTo(0);
        assertThat(row.get("activation_issued_at")).isNull();
        assertThat(row.get("activation_expires_at")).isNull();
    }

    private int tokenCount(String username) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM email_verification_tokens evt
                JOIN users u ON u.id = evt.user_id
                WHERE u.username = ?
                """, Integer.class, username);
    }

    private int activeTokenCount(String username) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM email_verification_tokens evt
                JOIN users u ON u.id = evt.user_id
                WHERE u.username = ? AND evt.used_at IS NULL
                """, Integer.class, username);
    }

    private UUID insertVerifiedRegisteredUser(String username, String email) {
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users(id, username, password_hash, account_status, platform_role,
                    email, email_normalized, email_verified_at, registration_source)
                VALUES (?, ?, ?, 'NORMAL', 'REGISTERED_USER', ?, ?, now(), 'PUBLIC')
                """, userId, username, passwordEncoder.encode("Example123!"), email, email);
        return userId;
    }

    private UUID insertSchool() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO schools(id, name, unified_code_type, unified_code, internal_code,
                    school_type, region, address, contact_name, contact_phone, contact_email, school_status)
                VALUES (?, ?, 'USCC', ?, ?, 'PRIMARY', 'Beijing', 'addr', 'contact', '123', 'school@example.com', 'NORMAL')
                """, id, "phase2-school-" + id, "USCC-" + id, "INT-" + id.toString().substring(0, 8));
        return id;
    }

    private String registerJson(String username, String email, String password, String confirmPassword) {
        return """
                {"username":"%s","email":"%s","password":"%s","confirmPassword":"%s"}
                """.formatted(username, email, password, confirmPassword);
    }

    private String loginJson(String username, String password) {
        return """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
