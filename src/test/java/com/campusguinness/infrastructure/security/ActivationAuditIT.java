package com.campusguinness.infrastructure.security;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ActivationAuditIT extends PostgreSqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    UUID userId; String username;

    @BeforeEach void setup() {
        userId = UUID.randomUUID(); username = "audit-" + UUID.randomUUID().toString().substring(0,8);
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", userId, username, "$2a$10$hash1234567890123456789012345", "NORMAL");
    }

    @AfterEach void cleanup() { jdbc.update("DELETE FROM users WHERE id=?", userId); }

    @Test void auditTableExistsAndCanBeWrittenTo() {
        UUID logId = UUID.randomUUID();
        jdbc.update("INSERT INTO activation_audit_logs(id,user_id,username_normalized,result,occurred_at) VALUES (?,?,?,'SUCCESS',now())", logId, userId, username);
        Integer count = jdbc.queryForObject("SELECT count(*) FROM activation_audit_logs WHERE id=?", Integer.class, logId);
        assertThat(count).isEqualTo(1);
    }

    @Test void auditTableDoesNotStorePasswords() {
        UUID logId = UUID.randomUUID();
        jdbc.update("INSERT INTO activation_audit_logs(id,user_id,username_normalized,result,occurred_at) VALUES (?,?,?,'SUCCESS',now())", logId, userId, username);
        var row = jdbc.queryForMap("SELECT * FROM activation_audit_logs WHERE id=?", logId);
        assertThat(row).doesNotContainKeys("temporary_password","new_password","password_hash","temp_pw");
    }
}
