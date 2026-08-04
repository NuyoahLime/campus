package com.campusguinness.infrastructure.security;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AccountActivationServiceIT extends com.campusguinness.PostgreSqlIntegrationTestSupport {

    @Autowired AccountActivationService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    UUID userId; String username; String tempPw; String tempPwHash;

    @BeforeEach void setup() {
        userId = UUID.randomUUID(); username = "act-" + UUID.randomUUID().toString().substring(0,8);
        tempPw = "tempPass123";
        tempPwHash = encoder.encode(tempPw);
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,activation_issued_at,activation_expires_at) VALUES (?,?,?,?,now(),now() + INTERVAL '72 hours')", userId, username, tempPwHash, "PENDING_ACTIVATION");
    }

    @AfterEach void cleanup() { jdbc.update("DELETE FROM users WHERE id=?", userId); }

    @Test void activateWithWrongPasswordReturns401() {
        var r = service.activate(username, "wrong", "NewPass123!", "1.2.3.4", "test");
        assertThat(r.code()).isEqualTo("ACTIVATION_CREDENTIALS_INVALID");
        assertThat(r.success()).isFalse();
    }

    @Test void userNotFoundReturns401() {
        var r = service.activate("noSuchUser", "x", "NewPass123!", "1.2.3.4", "test");
        assertThat(r.code()).isEqualTo("ACTIVATION_CREDENTIALS_INVALID");
    }

    @Test void alreadyNormalAccountReturns409() {
        jdbc.update("UPDATE users SET account_status='NORMAL',activation_issued_at=NULL,activation_expires_at=NULL WHERE id=?", userId);
        var r = service.activate(username, tempPw, "NewPass123!", "1.2.3.4", "test");
        assertThat(r.code()).isEqualTo("ACCOUNT_ALREADY_ACTIVATED");
    }

    @Test void passwordPolicyViolationReturns400() {
        var r = service.activate(username, tempPw, "short", "1.2.3.4", "test");
        assertThat(r.code()).isEqualTo("PASSWORD_TOO_SHORT");
    }

    @Test void auditRowsExistAfterActivation() {
        service.activate(username, tempPw, "NewPass123!", "1.2.3.4", "test");
        Integer count = jdbc.queryForObject("SELECT count(*) FROM activation_audit_logs WHERE username_normalized=?", Integer.class, username.trim());
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test void expiredTemporaryPasswordCannotActivate() {
        jdbc.update("UPDATE users SET activation_expires_at=now() - INTERVAL '1 minute' WHERE id=?", userId);
        var r = service.activate(username, tempPw, "NewPass123!", "1.2.3.4", "test");
        assertThat(r.code()).isEqualTo("ACTIVATION_CREDENTIALS_INVALID");
        assertThat(r.success()).isFalse();
    }

    @Test void successfulActivationClearsActivationTimestamps() {
        var r = service.activate(username, tempPw, "NewPass123!", "1.2.3.4", "test");
        assertThat(r.success()).isTrue();
        var row = jdbc.queryForMap("SELECT account_status, activation_issued_at, activation_expires_at FROM users WHERE id=?", userId);
        assertThat(row.get("account_status")).isEqualTo("NORMAL");
        assertThat(row.get("activation_issued_at")).isNull();
        assertThat(row.get("activation_expires_at")).isNull();
    }

    @Test void activationCanOnlySucceedOnce() {
        var r1 = service.activate(username, tempPw, "NewPass123!", "1.2.3.4", "test");
        assertThat(r1.success()).isTrue();
        var r2 = service.activate(username, tempPw, "AnotherPass456!", "1.2.3.4", "test");
        assertThat(r2.success()).isFalse();
        assertThat(r2.code()).isEqualTo("ACCOUNT_ALREADY_ACTIVATED");
    }

    @Test void missingActivationExpiryCannotActivate() {
        jdbc.update("UPDATE users SET activation_expires_at=NULL WHERE id=?", userId);
        var r = service.activate(username, tempPw, "NewPass123!", "1.2.3.4", "test");
        assertThat(r.success()).isFalse();
        assertThat(r.code()).isEqualTo("ACTIVATION_CREDENTIALS_INVALID");
    }
}
