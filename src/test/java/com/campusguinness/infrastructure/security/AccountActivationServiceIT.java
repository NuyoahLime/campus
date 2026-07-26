package com.campusguinness.infrastructure.security;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AccountActivationServiceIT extends com.campusguinness.PostgreSqlIntegrationTestSupport {

    @Autowired AccountActivationService service;
    @Autowired JdbcTemplate jdbc;

    UUID userId; String username; String tempPw;

    @BeforeEach void setup() {
        userId = UUID.randomUUID(); username = "act-" + UUID.randomUUID().toString().substring(0,8);
        tempPw = "tempPass123";
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", userId, username, "$2a$10$hAnonAsDummyHashForTest", "PENDING_ACTIVATION");
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
        jdbc.update("UPDATE users SET account_status='NORMAL' WHERE id=?", userId);
        var r = service.activate(username, tempPw, "NewPass123!", "1.2.3.4", "test");
        assertThat(r.code()).isEqualTo("ACCOUNT_ALREADY_ACTIVATED");
    }

    @Test void passwordPolicyViolationReturns400() {
        var r = service.activate(username, tempPw, "short", "1.2.3.4", "test");
        assertThat(r.code()).isEqualTo("PASSWORD_TOO_SHORT");
    }

    @Test void auditRowsExistAfterActivation() {
        jdbc.update("UPDATE users SET password_hash=? WHERE id=?", "$2a$10$hAnonDummyPassForTestU3E1", userId);
        service.activate(username, tempPw, "NewPass123!", "1.2.3.4", "test");
        // Audit written
        Integer count = jdbc.queryForObject("SELECT count(*) FROM activation_audit_logs WHERE username_normalized=?", Integer.class, username.trim());
        assertThat(count).isGreaterThanOrEqualTo(1);
    }
}
