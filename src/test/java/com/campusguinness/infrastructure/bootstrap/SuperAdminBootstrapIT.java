package com.campusguinness.infrastructure.bootstrap;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.identity.application.port.PasswordHasher;
import com.campusguinness.identity.application.service.BootstrapRefusedException;
import com.campusguinness.identity.application.service.SuperAdminBootstrapService;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * PostgreSQL integration tests for SUPER_ADMIN bootstrap.
 */
class SuperAdminBootstrapIT extends PostgreSqlIntegrationTestSupport {

    @Autowired private SuperAdminBootstrapService bootstrapService;
    @Autowired private PasswordHasher hasher;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanUsers() {
        // Delete in reverse FK order
        jdbc.update("DELETE FROM appeal_records");
        jdbc.update("DELETE FROM score_appeals");
        jdbc.update("DELETE FROM score_review_records");
        jdbc.update("DELETE FROM score_correction_records");
        jdbc.update("DELETE FROM abnormal_score_entries");
        jdbc.update("DELETE FROM score_attempts");
        jdbc.update("DELETE FROM ranking_entry_score_sources");
        jdbc.update("DELETE FROM ranking_entries");
        jdbc.update("DELETE FROM ranking_versions");
        jdbc.update("DELETE FROM l3_authorizations");
        jdbc.update("DELETE FROM ranking_definitions");
        jdbc.update("DELETE FROM project_rule_compatibilities");
        jdbc.update("DELETE FROM activity_projects");
        jdbc.update("DELETE FROM activities");
        jdbc.update("DELETE FROM project_rule_versions");
        jdbc.update("DELETE FROM challenge_projects");
        jdbc.update("DELETE FROM teacher_profiles");
        jdbc.update("DELETE FROM student_profiles");
        jdbc.update("DELETE FROM school_memberships");
        jdbc.update("DELETE FROM school_registrations");
        jdbc.update("DELETE FROM schools");
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM media_review_records");
        jdbc.update("DELETE FROM media");
        jdbc.update("DELETE FROM result_versions");
        jdbc.update("DELETE FROM activity_results");
        jdbc.update("DELETE FROM feedbacks");
        jdbc.update("DELETE FROM audit_records");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void createsSuperAdmin() {
        var result = bootstrapService.bootstrap("admin", "adminPass123");
        assertThat(result.platformRole()).isEqualTo("SUPER_ADMIN");
        assertThat(result.status()).isEqualTo("NORMAL");

        int count = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        assertThat(count).isEqualTo(1);
        String role = jdbc.queryForObject("SELECT platform_role FROM users WHERE username = ?", String.class, "admin");
        assertThat(role).isEqualTo("SUPER_ADMIN");
    }

    @Test
    void passwordHashNotNull() {
        bootstrapService.bootstrap("admin", "adminPass123");
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = ?", String.class, "admin");
        assertThat(hash).isNotNull();
        assertThat(hash).startsWith("$2a$");
    }

    @Test
    void passwordHashMatchesRaw() {
        bootstrapService.bootstrap("admin", "adminPass123");
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = ?", String.class, "admin");
        assertThat(hasher.matches("adminPass123", hash)).isTrue();
    }

    @Test
    void passwordHashDiffersFromRaw() {
        bootstrapService.bootstrap("admin", "adminPass123");
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = ?", String.class, "admin");
        assertThat(hash).isNotEqualTo("adminPass123");
    }

    @Test
    void loginFailuresInitializedToZero() {
        bootstrapService.bootstrap("admin", "adminPass123");
        int failures = jdbc.queryForObject("SELECT login_failures FROM users WHERE username = ?", Integer.class, "admin");
        assertThat(failures).isEqualTo(0);
    }

    @Test
    void lockedUntilInitializedToNull() {
        bootstrapService.bootstrap("admin", "adminPass123");
        Object locked = jdbc.queryForObject("SELECT locked_until FROM users WHERE username = ?", Object.class, "admin");
        assertThat(locked).isNull();
    }

    @Test
    void refusesWhenRegularUserExists() {
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)",
                UUID.randomUUID(), "existing", "h", "PENDING_ACTIVATION");

        assertThatThrownBy(() -> bootstrapService.bootstrap("admin", "adminPass123"))
                .isInstanceOf(BootstrapRefusedException.class);

        int count = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        assertThat(count).isEqualTo(1);
        int adminCount = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE platform_role = 'SUPER_ADMIN'", Integer.class);
        assertThat(adminCount).isEqualTo(0);
    }

    @Test
    void refusesWhenAdminAlreadyExists() {
        bootstrapService.bootstrap("firstAdmin", "firstPass123");
        int v1 = jdbc.queryForObject("SELECT version FROM users WHERE username = ?", Integer.class, "firstAdmin");

        assertThatThrownBy(() -> bootstrapService.bootstrap("secondAdmin", "secondPass456"))
                .isInstanceOf(BootstrapRefusedException.class);

        int count = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        assertThat(count).isEqualTo(1);
        int v2 = jdbc.queryForObject("SELECT version FROM users WHERE username = ?", Integer.class, "firstAdmin");
        assertThat(v2).isEqualTo(v1);
    }
}
