package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.port.SecureTokenHasher;
import com.campusguinness.identity.application.port.MailDeliveryPort;
import com.campusguinness.identity.application.port.MailMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
        "campus-guinness.security.cors.allowed-origins=http://localhost:5173",
        "app.mail.public-frontend-url=https://app.example.com"
})
class PublicRegistrationBackendIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SecureTokenHasher tokenHasher;
    @MockitoBean MailDeliveryPort mailDeliveryPort;

    @AfterEach
    void tearDown() {
        jdbc.update("""
                DELETE FROM activation_audit_logs
                WHERE failure_code = 'RESEND_VERIFICATION'
                  AND username_normalized LIKE 'resend:%'
                """);
        jdbc.update("""
                DELETE FROM school_memberships
                WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'phase2-%')
                """);
        jdbc.update("DELETE FROM users WHERE username LIKE 'phase2-%'");
        jdbc.update("DELETE FROM schools WHERE name LIKE 'phase2-school-%'");
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
    void concurrentDuplicateRegistrationCreatesExactlyOneAccount() throws Exception {
        String username = unique("phase2-concurrent-register");
        String email = username + "@example.com";

        List<MvcResult> results = runConcurrently(8, () -> mvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, email, "Example123!", "Example123!")))
                .andReturn());

        assertThat(results).hasSize(8);
        assertThat(results).filteredOn(result -> result.getResponse().getStatus() == 201).hasSize(1);
        assertThat(results).filteredOn(result -> result.getResponse().getStatus() == 409).hasSize(7);
        assertThat(results).filteredOn(result -> result.getResponse().getStatus() >= 500).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE username = ?",
                Integer.class, username)).isOne();
        assertThat(tokenCount(username)).isOne();
        assertThat(activeTokenCount(username)).isOne();
        verify(mailDeliveryPort, times(1)).send(any(MailMessage.class));
    }

    @Test
    void mailFailureDoesNotRollbackRegistration(CapturedOutput output) throws Exception {
        doThrow(new IllegalStateException(
                "smtp unavailable for mailfail@example.com https://app.example.com/verify-email?token=secret-token"))
                .when(mailDeliveryPort).send(any(MailMessage.class));
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
        assertThat(output).contains("Verification mail delivery failed");
        assertThat(output).contains("recipient=m***@example.com");
        assertThat(output).contains("errorType=IllegalStateException");
        assertThat(output).doesNotContain("mailfail@example.com");
        assertThat(output).doesNotContain("secret-token");
        assertThat(output).doesNotContain("/verify-email?token=");
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
    void sameVerificationTokenConcurrentUseOnlySucceedsOnce() throws Exception {
        String username = unique("phase2-concurrent-verify");
        register(username, "concurrent-verify@example.com");
        String token = lastRawToken();

        List<MvcResult> results = runConcurrently(8, () -> mvc.perform(post("/api/v1/auth/verify-email")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andReturn());

        assertThat(results).hasSize(8);
        assertThat(results).filteredOn(result -> result.getResponse().getStatus() == 204).hasSize(1);
        assertThat(results).filteredOn(result -> result.getResponse().getStatus() == 400)
                .hasSize(7)
                .allSatisfy(result ->
                        assertThat(result.getResponse().getContentAsString())
                                .contains("\"code\":\"EMAIL_VERIFICATION_INVALID\""));
        assertThat(results).filteredOn(result -> result.getResponse().getStatus() >= 500).isEmpty();
        assertThat(jdbc.queryForObject("SELECT email_verified_at IS NOT NULL FROM users WHERE username=?",
                Boolean.class, username)).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT used_at IS NOT NULL FROM email_verification_tokens
                WHERE token_hash = ?
                """, Boolean.class, tokenHasher.hash(token))).isTrue();
    }

    @Test
    void invalidVerificationCasesReturnGenericError() throws Exception {
        expectInvalidVerification("{\"token\":\"not-a-real-token\"}");
    }

    @Test
    void blankVerificationTokenReturnsGenericError() throws Exception {
        expectInvalidVerification("{\"token\":\"\"}");
        expectInvalidVerification("{\"token\":\"   \"}");
    }

    @Test
    void missingVerificationTokenReturnsGenericError() throws Exception {
        expectInvalidVerification("{}");
        expectInvalidVerification("{\"token\":null}");
    }

    @Test
    void expiredVerificationReturnsGenericError() throws Exception {
        String username = unique("phase2-expired");
        register(username, "expired@example.com");
        String token = lastRawToken();
        jdbc.update("""
                UPDATE email_verification_tokens
                SET created_at = now() - INTERVAL '2 minutes',
                    expires_at = now() - INTERVAL '1 minute'
                WHERE token_hash = ?
                """, tokenHasher.hash(token));

        expectInvalidVerification("{\"token\":\"" + token + "\"}");
    }

    @Test
    void usedVerificationReturnsGenericError() throws Exception {
        String username = unique("phase2-used");
        register(username, "used@example.com");
        String token = lastRawToken();
        verifyEmail(token);

        expectInvalidVerification("{\"token\":\"" + token + "\"}");
    }

    @Test
    void wrongPurposeReturnsGenericError() throws Exception {
        String username = unique("phase2-purpose");
        register(username, "purpose@example.com");
        String token = lastRawToken();
        jdbc.update("""
                UPDATE email_verification_tokens
                SET purpose = 'RECOVERY_EMAIL'
                WHERE token_hash = ?
                """, tokenHasher.hash(token));

        expectInvalidVerification("{\"token\":\"" + token + "\"}");
    }

    @Test
    void targetEmailMismatchReturnsGenericError() throws Exception {
        String username = unique("phase2-email-mismatch");
        register(username, "mismatch-token@example.com");
        String token = lastRawToken();
        jdbc.update("""
                UPDATE email_verification_tokens
                SET target_email_normalized = 'different@example.com'
                WHERE token_hash = ?
                """, tokenHasher.hash(token));

        expectInvalidVerification("{\"token\":\"" + token + "\"}");
    }

    @Test
    void wrongUserStatusReturnsGenericError() throws Exception {
        String username = unique("phase2-wrong-status");
        register(username, "wrong-status@example.com");
        String token = lastRawToken();
        jdbc.update("UPDATE users SET account_status = 'DISABLED' WHERE username = ?", username);

        expectInvalidVerification("{\"token\":\"" + token + "\"}");
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
                            .with(remoteAddr("203.0.113.44"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\"}"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.message").value("If an unverified account exists, a verification email will be sent."));
        }

        verify(mailDeliveryPort, times(5)).send(any(MailMessage.class));
    }

    @Test
    void spoofedForwardedForIsIgnored() throws Exception {
        String username = unique("phase2-spoof");
        String email = username + "@example.com";
        register(username, email);
        reset(mailDeliveryPort);

        for (int i = 0; i < 6; i++) {
            mvc.perform(post("/api/v1/auth/resend-verification")
                            .with(csrf())
                            .with(remoteAddr("203.0.113.10"))
                            .header("X-Forwarded-For", "198.51.100." + i)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\"}"))
                    .andExpect(status().isAccepted());
        }

        verify(mailDeliveryPort, times(5)).send(any(MailMessage.class));
    }

    @Test
    void remoteAddressIsUsedForRateLimiting() throws Exception {
        String remoteAddress = "203.0.113.99";

        for (int i = 0; i < 6; i++) {
            mvc.perform(post("/api/v1/auth/resend-verification")
                            .with(csrf())
                            .with(remoteAddr(remoteAddress))
                            .header("X-Forwarded-For", "198.51.100." + i)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"unknown-" + i + "@example.com\"}"))
                    .andExpect(status().isAccepted());
        }

        String ipKey = "resend:ip:" + tokenHasher.hash(remoteAddress);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM activation_audit_logs
                WHERE username_normalized = ?
                """, Integer.class, ipKey)).isEqualTo(6);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM activation_audit_logs
                WHERE username_normalized = ? AND result = 'RATE_LIMITED'
                """, Integer.class, ipKey)).isOne();
        verify(mailDeliveryPort, never()).send(any(MailMessage.class));
    }

    @Test
    void concurrentResendNeverExceedsFiveDeliveries() throws Exception {
        String username = unique("phase2-concurrent-resend");
        String email = username + "@example.com";
        register(username, email);
        reset(mailDeliveryPort);

        List<MvcResult> results = runConcurrently(12, () -> mvc.perform(post("/api/v1/auth/resend-verification")
                        .with(csrf())
                        .with(remoteAddr("203.0.113.55"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andReturn());

        assertThat(results).hasSize(12);
        assertThat(results).allSatisfy(result ->
                assertThat(result.getResponse().getStatus()).isEqualTo(202));
        assertThat(results).noneSatisfy(result ->
                assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(500));
        verify(mailDeliveryPort, atMost(5)).send(any(MailMessage.class));
        assertThat(activeTokenCount(username)).isOne();
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

    @Test
    void registeredUserCannotAccessTeacherApi() throws Exception {
        var sessionCookie = loginVerifiedRegisteredUser(unique("phase2-teacher-deny"), "teacher-deny@example.com");

        assertRegisteredUserGate(get("/api/v1/teacher/responsible-projects").cookie(sessionCookie));
    }

    @Test
    void registeredUserCannotAccessSchoolAdminApi() throws Exception {
        var sessionCookie = loginVerifiedRegisteredUser(unique("phase2-school-admin-deny"), "school-admin-deny@example.com");

        assertRegisteredUserGate(get("/api/v1/school-admin/score-attempts/mine").cookie(sessionCookie));
    }

    @Test
    void registeredUserCannotAccessGenericBusinessApi() throws Exception {
        var sessionCookie = loginVerifiedRegisteredUser(unique("phase2-business-deny"), "business-deny@example.com");

        assertRegisteredUserGate(get("/api/v1/score-attempts/mine").cookie(sessionCookie));
    }

    @Test
    void formalStudentIsNotBlockedByRegisteredUserFilter() throws Exception {
        var sessionCookie = loginFormalSchoolUser(unique("phase2-formal-student"), "STUDENT");

        assertNotRegisteredUserGate(get("/api/v1/student/scores").cookie(sessionCookie));
    }

    @Test
    void formalTeacherIsNotBlockedByRegisteredUserFilter() throws Exception {
        var sessionCookie = loginFormalSchoolUser(unique("phase2-formal-teacher"), "TEACHER");

        assertNotRegisteredUserGate(get("/api/v1/teacher/responsible-projects").cookie(sessionCookie));
    }

    @Test
    void schoolAdminIsNotBlockedByRegisteredUserFilter() throws Exception {
        var sessionCookie = loginFormalSchoolUser(unique("phase2-formal-admin"), "SCHOOL_ADMIN");

        assertNotRegisteredUserGate(get("/api/v1/school-admin/score-attempts/mine").cookie(sessionCookie));
    }

    @Test
    void superAdminIsNotBlockedByRegisteredUserFilter() throws Exception {
        var sessionCookie = loginSuperAdmin(unique("phase2-formal-super"));

        assertNotRegisteredUserGate(get("/api/v1/admin/activities").cookie(sessionCookie));
    }

    @Test
    void adminProvisionedActivationFlowStillWorks() throws Exception {
        String username = unique("phase2-legacy-activation");
        String temporaryPassword = "TempPass123!";
        String newPassword = "NewPass123!";
        jdbc.update("""
                INSERT INTO users(id, username, password_hash, account_status,
                    platform_role, activation_issued_at, activation_expires_at, registration_source)
                VALUES (?, ?, ?, 'PENDING_ACTIVATION',
                    'SUPER_ADMIN', now(), now() + INTERVAL '72 hours', 'ADMIN_PROVISIONED')
                """, UUID.randomUUID(), username, passwordEncoder.encode(temporaryPassword));

        mvc.perform(post("/api/v1/auth/activate").with(csrf())
                        .with(remoteAddr("203.0.113.77"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "temporaryPassword":"%s",
                                  "newPassword":"%s",
                                  "confirmPassword":"%s"
                                }
                                """.formatted(username, temporaryPassword, newPassword, newPassword)))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("""
                SELECT account_status = 'NORMAL'
                    AND activation_issued_at IS NULL
                    AND activation_expires_at IS NULL
                FROM users
                WHERE username = ?
                """, Boolean.class, username)).isTrue();

        mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, newPassword)))
                .andExpect(status().isOk());
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

    private void expectInvalidVerification(String json) throws Exception {
        mvc.perform(post("/api/v1/auth/verify-email").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID"))
                .andExpect(jsonPath("$.message").value("The email verification link is invalid or expired."));
    }

    private jakarta.servlet.http.Cookie loginVerifiedRegisteredUser(String username, String email) throws Exception {
        register(username, email);
        verifyEmail(lastRawToken());
        var login = mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, "Example123!")))
                .andExpect(status().isOk())
                .andReturn();
        var sessionCookie = login.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();
        return sessionCookie;
    }

    private jakarta.servlet.http.Cookie loginFormalSchoolUser(String username, String roleInSchool) throws Exception {
        UUID userId = insertNormalUser(username, null);
        UUID schoolId = insertSchool();
        jdbc.update("""
                INSERT INTO school_memberships(id, user_id, school_id, role_in_school, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, UUID.randomUUID(), userId, schoolId, roleInSchool);
        return login(username, "Example123!");
    }

    private jakarta.servlet.http.Cookie loginSuperAdmin(String username) throws Exception {
        insertNormalUser(username, "SUPER_ADMIN");
        return login(username, "Example123!");
    }

    private jakarta.servlet.http.Cookie login(String username, String password) throws Exception {
        var login = mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        var sessionCookie = login.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();
        return sessionCookie;
    }

    private UUID insertNormalUser(String username, String platformRole) {
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users(id, username, password_hash, account_status, platform_role, registration_source)
                VALUES (?, ?, ?, 'NORMAL', ?, 'ADMIN_PROVISIONED')
                """, userId, username, passwordEncoder.encode("Example123!"), platformRole);
        return userId;
    }

    private void assertRegisteredUserGate(RequestBuilder request) throws Exception {
        mvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REGISTERED_USER_ONBOARDING_REQUIRED"));
    }

    private void assertNotRegisteredUserGate(RequestBuilder request) throws Exception {
        var result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("REGISTERED_USER_ONBOARDING_REQUIRED");
    }

    private List<MvcResult> runConcurrently(int count, Callable<MvcResult> operation) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(executor.submit(() -> {
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    return operation.call();
                }));
            }
            start.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get(15, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private RequestPostProcessor remoteAddr(String remoteAddress) {
        return request -> {
            request.setRemoteAddr(remoteAddress);
            return request;
        };
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
