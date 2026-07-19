package com.campusguinness.infrastructure.security;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.identity.application.port.PasswordHasher;
import com.campusguinness.identity.application.port.UserAccountProvisioningPort;
import com.campusguinness.identity.application.port.UserRepository;
import com.campusguinness.identity.internal.domain.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * PostgreSQL integration tests for user account creation.
 */
class UserAccountProvisioningIT extends PostgreSqlIntegrationTestSupport {

    @Autowired private UserAccountProvisioningPort provisioning;
    @Autowired private UserRepository userRepo;
    @Autowired private PasswordHasher hasher;
    @Autowired private JdbcTemplate jdbc;

    private UUID userId;
    private String username;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        username = "prov-test-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Nested
    class CreateNewUser {

        @Test
        void insertsOneRow() {
            String hash = hasher.hash("validPassword123");
            var user = User.create(new User.Builder().id(new UserId(userId)).username(username));
            var saved = provisioning.create(user, hash);

            assertThat(saved.id().value()).isEqualTo(userId);
            int count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId);
            assertThat(count).isEqualTo(1);
        }

        @Test
        void passwordHashNotNull() {
            String hash = hasher.hash("validPassword123");
            var user = User.create(new User.Builder().id(new UserId(userId)).username(username));
            provisioning.create(user, hash);

            String stored = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, userId);
            assertThat(stored).isNotNull();
            assertThat(stored).startsWith("$2a$");
        }

        @Test
        void passwordHashDiffersFromRaw() {
            String hash = hasher.hash("validPassword123");
            var user = User.create(new User.Builder().id(new UserId(userId)).username(username));
            provisioning.create(user, hash);

            String stored = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, userId);
            assertThat(stored).isNotEqualTo("validPassword123");
        }

        @Test
        void passwordHashMatchesRaw() {
            String raw = "validPassword123";
            String hash = hasher.hash(raw);
            var user = User.create(new User.Builder().id(new UserId(userId)).username(username));
            provisioning.create(user, hash);

            String stored = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, userId);
            assertThat(hasher.matches(raw, stored)).isTrue();
        }

        @Test
        void loginFailuresInitializedToZero() {
            String hash = hasher.hash("validPassword123");
            var user = User.create(new User.Builder().id(new UserId(userId)).username(username));
            provisioning.create(user, hash);

            int failures = jdbc.queryForObject("SELECT login_failures FROM users WHERE id = ?", Integer.class, userId);
            assertThat(failures).isEqualTo(0);
        }

        @Test
        void lockedUntilInitializedToNull() {
            String hash = hasher.hash("validPassword123");
            var user = User.create(new User.Builder().id(new UserId(userId)).username(username));
            provisioning.create(user, hash);

            Object locked = jdbc.queryForObject("SELECT locked_until FROM users WHERE id = ?", Object.class, userId);
            assertThat(locked).isNull();
        }

        @Test
        void platformRoleIsNull() {
            String hash = hasher.hash("validPassword123");
            var user = User.create(new User.Builder().id(new UserId(userId)).username(username));
            provisioning.create(user, hash);

            String role = jdbc.queryForObject("SELECT platform_role FROM users WHERE id = ?", String.class, userId);
            assertThat(role).isNull();
        }

        @Test
        void initialStatusIsPendingActivation() {
            String hash = hasher.hash("validPassword123");
            var user = User.create(new User.Builder().id(new UserId(userId)).username(username));
            var saved = provisioning.create(user, hash);

            assertThat(saved.status()).isEqualTo(AccountStatus.PENDING_ACTIVATION);
            String status = jdbc.queryForObject("SELECT account_status FROM users WHERE id = ?", String.class, userId);
            assertThat(status).isEqualTo("PENDING_ACTIVATION");
        }

        @Test
        void versionInitializedCorrectly() {
            String hash = hasher.hash("validPassword123");
            var user = User.create(new User.Builder().id(new UserId(userId)).username(username));
            provisioning.create(user, hash);

            int version = jdbc.queryForObject("SELECT version FROM users WHERE id = ?", Integer.class, userId);
            assertThat(version).isGreaterThanOrEqualTo(0);
        }

        @Test
        void emptyDatabaseFirstRegularUserCreation() {
            // This test verifies creation works from a clean state
            String hash = hasher.hash("firstUserPassword1");
            var user = User.create(new User.Builder().id(new UserId(userId)).username(username));
            var saved = provisioning.create(user, hash);

            assertThat(saved).isNotNull();
            assertThat(saved.status()).isEqualTo(AccountStatus.PENDING_ACTIVATION);
            assertThat(saved.platformRole()).isNull(); // NOT SUPER_ADMIN
        }

        @Test
        void duplicateUsernameViolatesUniqueConstraint() {
            String hash = hasher.hash("validPassword123");
            var user1 = User.create(new User.Builder().id(new UserId(userId)).username(username));
            provisioning.create(user1, hash);

            UUID id2 = UUID.randomUUID();
            var user2 = User.create(new User.Builder().id(new UserId(id2)).username(username));
            assertThatThrownBy(() -> provisioning.create(user2, hash))
                    .isNotNull(); // constraint violation
        }
    }

    @Nested
    class UpdateStillPreservesAuthFields {

        @Test
        void passwordHashUnchangedAfterDomainUpdate() {
            String hash = hasher.hash("validPassword123");
            var user = User.create(new User.Builder().id(new UserId(userId)).username(username));
            provisioning.create(user, hash);

            // Now update via generic repository
            var loaded = userRepo.findById(new UserId(userId)).orElseThrow();
            loaded.activate(); // PENDING_ACTIVATION → NORMAL
            loaded.lock();     // NORMAL → LOCKED
            userRepo.save(loaded);

            String stored = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, userId);
            assertThat(stored).isEqualTo(hash);
        }

        @Test
        void loginFailuresUnchangedAfterDomainUpdate() {
            String hash = hasher.hash("validPassword123");
            var user = User.create(new User.Builder().id(new UserId(userId)).username(username));
            provisioning.create(user, hash);

            var loaded = userRepo.findById(new UserId(userId)).orElseThrow();
            loaded.activate(); // PENDING_ACTIVATION → NORMAL
            loaded.lock();     // NORMAL → LOCKED
            userRepo.save(loaded);

            int failures = jdbc.queryForObject("SELECT login_failures FROM users WHERE id = ?", Integer.class, userId);
            assertThat(failures).isEqualTo(0);
        }

        @Test
        void lockedUntilUnchangedAfterDomainUpdate() {
            String hash = hasher.hash("validPassword123");
            var user = User.create(new User.Builder().id(new UserId(userId)).username(username));
            provisioning.create(user, hash);

            var loaded = userRepo.findById(new UserId(userId)).orElseThrow();
            loaded.activate(); // PENDING_ACTIVATION → NORMAL
            loaded.lock();     // NORMAL → LOCKED
            userRepo.save(loaded);

            Object locked = jdbc.queryForObject("SELECT locked_until FROM users WHERE id = ?", Object.class, userId);
            assertThat(locked).isNull();
        }

        @Test
        void versionIncrementsOnUpdate() {
            String hash = hasher.hash("validPassword123");
            var user = User.create(new User.Builder().id(new UserId(userId)).username(username));
            provisioning.create(user, hash);

            int v1 = jdbc.queryForObject("SELECT version FROM users WHERE id = ?", Integer.class, userId);
            var loaded = userRepo.findById(new UserId(userId)).orElseThrow();
            loaded.activate(); // PENDING_ACTIVATION → NORMAL
            loaded.lock();     // NORMAL → LOCKED
            userRepo.save(loaded);
            int v2 = jdbc.queryForObject("SELECT version FROM users WHERE id = ?", Integer.class, userId);
            assertThat(v2).isGreaterThan(v1);
        }

        @Test
        void updateDoesNotInsertDuplicate() {
            String hash = hasher.hash("validPassword123");
            var user = User.create(new User.Builder().id(new UserId(userId)).username(username));
            provisioning.create(user, hash);

            var loaded = userRepo.findById(new UserId(userId)).orElseThrow();
            loaded.activate(); // PENDING_ACTIVATION → NORMAL
            loaded.lock();     // NORMAL → LOCKED
            userRepo.save(loaded);

            int count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId);
            assertThat(count).isEqualTo(1);
        }
    }
}
