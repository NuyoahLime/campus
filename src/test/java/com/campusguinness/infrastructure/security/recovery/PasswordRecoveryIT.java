package com.campusguinness.infrastructure.security.recovery;

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
class PasswordRecoveryIT {

    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired PasswordRecoveryService service;

    private UUID targetId;
    private String targetUsername;
    private String oldHash;

    @BeforeEach
    void setup() {
        targetId = UUID.randomUUID();
        targetUsername = "recovery-test-" + UUID.randomUUID().toString().substring(0, 8);
        oldHash = encoder.encode("oldPassword1");
        jdbc.update("DELETE FROM users");
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)",
                targetId, targetUsername, oldHash, "NORMAL", "SUPER_ADMIN");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM users WHERE id = ?", targetId);
    }

    private PasswordRecoveryProperties validProps() {
        var p = new PasswordRecoveryProperties();
        p.setEnabled(true);
        p.setTargetUserId(targetId);
        p.setTargetUsername(targetUsername);
        p.setExpectedStatus("NORMAL");
        p.setExpectedPlatformRole("SUPER_ADMIN");
        p.setNewPassword("newValidPass1");
        p.setInvalidateExistingSessions(true);
        return p;
    }

    @Test void successfulRecovery() {
        var result = service.recover(validProps());
        assertThat(result.success()).isTrue();
        assertThat(result.exitCode()).isEqualTo(0);

        // Verify password changed
        String newHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, targetId);
        assertThat(newHash).isNotEqualTo(oldHash);
        assertThat(encoder.matches("newValidPass1", newHash)).isTrue();

        // Verify other fields unchanged
        String status = jdbc.queryForObject("SELECT account_status FROM users WHERE id = ?", String.class, targetId);
        assertThat(status).isEqualTo("NORMAL");
        String role = jdbc.queryForObject("SELECT platform_role FROM users WHERE id = ?", String.class, targetId);
        assertThat(role).isEqualTo("SUPER_ADMIN");
    }

    @Test void targetNotFound() {
        var p = validProps();
        p.setTargetUserId(UUID.randomUUID());
        var result = service.recover(p);
        assertThat(result.success()).isFalse();
        assertThat(result.exitCode()).isEqualTo(30);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, targetId)).isEqualTo(oldHash);
    }

    @Test void statusMismatch() {
        var p = validProps();
        p.setExpectedStatus("DISABLED");
        var result = service.recover(p);
        assertThat(result.exitCode()).isEqualTo(33);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, targetId)).isEqualTo(oldHash);
    }

    @Test void roleMismatch() {
        var p = validProps();
        p.setExpectedPlatformRole("NOT_SUPER_ADMIN");
        var result = service.recover(p);
        assertThat(result.exitCode()).isEqualTo(34);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, targetId)).isEqualTo(oldHash);
    }

    @Test void usernameMismatch() {
        var p = validProps();
        p.setTargetUsername("wrongUsername");
        var result = service.recover(p);
        assertThat(result.success()).isFalse();
        assertThat(result.exitCode()).isEqualTo(32);
    }

    @Test void passwordPolicyRejected() {
        var p = validProps();
        p.setNewPassword("short");
        var result = service.recover(p);
        assertThat(result.exitCode()).isEqualTo(40);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, targetId)).isEqualTo(oldHash);
    }

    @Test void sessionsClearedOnRecovery() {
        // Insert a session for the target user
        jdbc.update("INSERT INTO spring_session(primary_id,session_id,creation_time,last_access_time,max_inactive_interval,expiry_time,principal_name) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), 1000L, 1000L, 1800, 9999999999L, targetUsername);

        var result = service.recover(validProps());
        assertThat(result.success()).isTrue();
        assertThat(result.sessionsDeleted()).isGreaterThanOrEqualTo(1);

        int count = jdbc.queryForObject("SELECT COUNT(*) FROM spring_session WHERE principal_name = ?", Integer.class, targetUsername);
        assertThat(count).isEqualTo(0);
    }

    @Test void otherUserSessionsUnaffected() {
        // Insert another user + their session
        UUID otherId = UUID.randomUUID();
        String otherUser = "other-" + UUID.randomUUID().toString().substring(0, 6);
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)",
                otherId, otherUser, encoder.encode("otherPass1"), "NORMAL");
        jdbc.update("INSERT INTO spring_session(primary_id,session_id,creation_time,last_access_time,max_inactive_interval,expiry_time,principal_name) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), 1000L, 1000L, 1800, 9999999999L, otherUser);

        try {
            var result = service.recover(validProps());
            assertThat(result.success()).isTrue();

            int otherSessions = jdbc.queryForObject("SELECT COUNT(*) FROM spring_session WHERE principal_name = ?", Integer.class, otherUser);
            assertThat(otherSessions).isEqualTo(1); // other user's session preserved
        } finally {
            jdbc.update("DELETE FROM spring_session WHERE principal_name = ?", otherUser);
            jdbc.update("DELETE FROM users WHERE id = ?", otherId);
        }
    }
}
