package com.campusguinness.infrastructure.security;

import com.campusguinness.PostgreSqlIntegrationTestSupport;

import com.campusguinness.identity.application.query.AuthenticationAccountQuery;
import com.campusguinness.identity.application.port.UserRepository;
import com.campusguinness.identity.internal.domain.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * PostgreSQL integration tests for:
 * 1. AuthenticationAccountQuery.findByLoginName
 * 2. User UPDATE preserves auth fields (passwordHash, loginFailures, lockedUntil)
 */
class AuthenticationQueryAdapterIT extends PostgreSqlIntegrationTestSupport {

    @Autowired AuthenticationAccountQuery accountQuery;
    @Autowired UserRepository userRepo;
    @Autowired JdbcTemplate jdbc;

    private UUID userId;
    private String username;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        username = "auth-test-" + UUID.randomUUID().toString().substring(0, 8);
        // Minimal insert matching existing test patterns
        jdbc.update(
                "INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)",
                userId, username, "$2a$12$testHashValueHere1234567890abc", "NORMAL", "SUPER_ADMIN");
        // Set auth-specific fields via update (use Timestamp for timestamptz compatibility)
        Timestamp lockTime = Timestamp.from(Instant.parse("2026-07-16T10:00:00Z"));
        jdbc.update("UPDATE users SET login_failures = ?, locked_until = ? WHERE id = ?",
                2, lockTime, userId);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Nested
    class FindByLoginName {

        @Test
        void returnsAccountWhenFound() {
            var result = accountQuery.findByLoginName(username);
            assertThat(result).isPresent();
            assertThat(result.get().userId()).isEqualTo(userId);
            assertThat(result.get().loginName()).isEqualTo(username);
            assertThat(result.get().passwordHash()).isEqualTo("$2a$12$testHashValueHere1234567890abc");
            assertThat(result.get().accountStatus()).isEqualTo("NORMAL");
            assertThat(result.get().platformRole()).isEqualTo("SUPER_ADMIN");
        }

        @Test
        void returnsEmptyWhenNotFound() {
            var result = accountQuery.findByLoginName("nonexistent-user-" + UUID.randomUUID());
            assertThat(result).isEmpty();
        }

        @Test
        void passwordHashReadAsStored() {
            var result = accountQuery.findByLoginName(username);
            assertThat(result).isPresent();
            assertThat(result.get().passwordHash()).startsWith("$2a$12$");
        }

        @Test
        void nullPlatformRoleReturnsNull() {
            UUID id = UUID.randomUUID();
            String un = "null-role-" + UUID.randomUUID().toString().substring(0, 8);
            jdbc.update(
                    "INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)",
                    id, un, "$2a$12$hash", "NORMAL", null);
            try {
                var result = accountQuery.findByLoginName(un);
                assertThat(result).isPresent();
                assertThat(result.get().platformRole()).isNull();
            } finally {
                jdbc.update("DELETE FROM users WHERE id = ?", id);
            }
        }
    }

    @Nested
    class UpdatePreservesAuthFields {

        @Test
        void passwordHashUnchangedAfterDomainUpdate() {
            var opt = userRepo.findById(new UserId(userId));
            assertThat(opt).isPresent();
            var user = opt.orElseThrow();

            // Use a valid state transition: NORMAL -> LOCKED
            user.lock();
            userRepo.save(user);

            // Verify password_hash unchanged
            String hash = jdbc.queryForObject(
                    "SELECT password_hash FROM users WHERE id = ?", String.class, userId);
            assertThat(hash).isEqualTo("$2a$12$testHashValueHere1234567890abc");
        }

        @Test
        void loginFailuresUnchangedAfterDomainUpdate() {
            var user = userRepo.findById(new UserId(userId)).orElseThrow();
            // Use a valid transition that changes domain state but not auth fields
            user.disable();
            userRepo.save(user);

            int failures = jdbc.queryForObject(
                    "SELECT login_failures FROM users WHERE id = ?", Integer.class, userId);
            assertThat(failures).isEqualTo(2);
        }

        @Test
        void lockedUntilUnchangedAfterDomainUpdate() {
            // Create a fresh user for this test to avoid state conflicts
            UUID freshId = UUID.randomUUID();
            String freshName = "fresh-" + UUID.randomUUID().toString().substring(0, 8);
            jdbc.update(
                    "INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)",
                    freshId, freshName, "$2a$12$hash", "NORMAL", null);
            Timestamp lockTime = Timestamp.from(Instant.parse("2026-07-16T10:00:00Z"));
            jdbc.update("UPDATE users SET login_failures = ?, locked_until = ? WHERE id = ?",
                    3, lockTime, freshId);
            try {
                var user = userRepo.findById(new UserId(freshId)).orElseThrow();
                user.lock();
                userRepo.save(user);

                Instant locked = jdbc.queryForObject(
                        "SELECT locked_until FROM users WHERE id = ?", Instant.class, freshId);
                assertThat(locked).isEqualTo(Instant.parse("2026-07-16T10:00:00Z"));
            } finally {
                jdbc.update("DELETE FROM users WHERE id = ?", freshId);
            }
        }

        @Test
        void versionIncrementsOnUpdate() {
            int v1 = jdbc.queryForObject(
                    "SELECT version FROM users WHERE id = ?", Integer.class, userId);
            var user = userRepo.findById(new UserId(userId)).orElseThrow();
            user.lock();
            userRepo.save(user);
            int v2 = jdbc.queryForObject(
                    "SELECT version FROM users WHERE id = ?", Integer.class, userId);
            assertThat(v2).isGreaterThan(v1);
        }

        @Test
        void updateDoesNotInsertDuplicateRecord() {
            var user = userRepo.findById(new UserId(userId)).orElseThrow();
            user.lock();
            userRepo.save(user);

            int count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId);
            assertThat(count).isEqualTo(1);
        }

        @Test
        void primaryKeyUnchangedAfterUpdate() {
            var user = userRepo.findById(new UserId(userId)).orElseThrow();
            user.lock();
            userRepo.save(user);

            UUID id = jdbc.queryForObject(
                    "SELECT id FROM users WHERE id = ?", UUID.class, userId);
            assertThat(id).isEqualTo(userId);
        }
    }
}
