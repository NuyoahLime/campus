package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.internal.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DisplayName("UserPersistenceMapper")
class UserPersistenceMapperTest {
    @Nested class AllStates {
        @ParameterizedTest @EnumSource(AccountStatus.class)
        void restoresState(AccountStatus s) {
            var e = entity(s.name());
            var u = UserPersistenceMapper.toDomain(e);
            assertThat(u.status()).isEqualTo(s);
            assertThat(u.domainEvents()).isEmpty();
        }
    }
    @Nested class Fields {
        @Test void keepsUsername() { var e=entity("NORMAL"); e.setUsername("testuser"); assertThat(UserPersistenceMapper.toDomain(e).username()).isEqualTo("testuser"); }
        @Test void keepsPlatformRole() { var e=entity("NORMAL"); e.setPlatformRole("SUPER_ADMIN"); assertThat(UserPersistenceMapper.toDomain(e).platformRole()).isEqualTo("SUPER_ADMIN"); }
    }
    @Nested class ToEntity {
        @Test void mapsToEntity() {
            var u = User.create(new User.Builder().id(new UserId(UUID.randomUUID())).username("test"));
            assertThat(UserPersistenceMapper.toEntity(u).getAccountStatus()).isEqualTo("PENDING_ACTIVATION");
        }
    }
    @Nested class ToNewEntity {
        @Test void setsPasswordHash() {
            var u = User.create(new User.Builder().id(new UserId(UUID.randomUUID())).username("test"));
            var e = UserPersistenceMapper.toNewEntity(u, "$2a$12$hashed");
            assertThat(e.getPasswordHash()).isEqualTo("$2a$12$hashed");
        }
        @Test void setsLoginFailuresToZero() {
            var u = User.create(new User.Builder().id(new UserId(UUID.randomUUID())).username("test"));
            var e = UserPersistenceMapper.toNewEntity(u, "$2a$12$hash");
            assertThat(e.getLoginFailures()).isEqualTo(0);
        }
        @Test void setsLockedUntilToNull() {
            var u = User.create(new User.Builder().id(new UserId(UUID.randomUUID())).username("test"));
            var e = UserPersistenceMapper.toNewEntity(u, "$2a$12$hash");
            assertThat(e.getLockedUntil()).isNull();
        }
        @Test void preservesDomainFields() {
            var id = UUID.randomUUID();
            var u = User.create(new User.Builder().id(new UserId(id)).username("testuser"));
            var e = UserPersistenceMapper.toNewEntity(u, "$2a$12$hash");
            assertThat(e.getId()).isEqualTo(id);
            assertThat(e.getUsername()).isEqualTo("testuser");
            assertThat(e.getAccountStatus()).isEqualTo("PENDING_ACTIVATION");
            assertThat(e.getPlatformRole()).isNull();
        }
    }
    @Nested class UpdateEntity {
        @Test void updatesDomainFields() {
            var existing = entity("NORMAL");
            existing.setUsername("oldname");
            existing.setPlatformRole(null);
            var domain = User.reconstitute(
                    new User.Builder().id(new UserId(existing.getId())).username("newname").platformRole("SUPER_ADMIN"),
                    AccountStatus.DISABLED, java.util.List.of());
            UserPersistenceMapper.updateEntity(existing, domain);
            assertThat(existing.getUsername()).isEqualTo("newname");
            assertThat(existing.getAccountStatus()).isEqualTo("DISABLED");
            assertThat(existing.getPlatformRole()).isEqualTo("SUPER_ADMIN");
        }
        @Test void preservesPasswordHash() {
            var existing = entity("NORMAL");
            existing.setPasswordHash("$2a$12$originalHashValue");
            var domain = User.reconstitute(
                    new User.Builder().id(new UserId(existing.getId())).username("u").platformRole(null),
                    AccountStatus.LOCKED, java.util.List.of());
            UserPersistenceMapper.updateEntity(existing, domain);
            assertThat(existing.getPasswordHash()).isEqualTo("$2a$12$originalHashValue");
        }
        @Test void preservesLoginFailures() {
            var existing = entity("NORMAL");
            existing.setLoginFailures(3);
            var domain = User.reconstitute(
                    new User.Builder().id(new UserId(existing.getId())).username("u").platformRole(null),
                    AccountStatus.NORMAL, java.util.List.of());
            UserPersistenceMapper.updateEntity(existing, domain);
            assertThat(existing.getLoginFailures()).isEqualTo(3);
        }
        @Test void preservesLockedUntil() {
            var existing = entity("NORMAL");
            Instant lockTime = Instant.parse("2026-07-16T10:00:00Z");
            existing.setLockedUntil(lockTime);
            var domain = User.reconstitute(
                    new User.Builder().id(new UserId(existing.getId())).username("u").platformRole(null),
                    AccountStatus.NORMAL, java.util.List.of());
            UserPersistenceMapper.updateEntity(existing, domain);
            assertThat(existing.getLockedUntil()).isEqualTo(lockTime);
        }
        @Test void preservesId() {
            var existing = entity("NORMAL");
            UUID originalId = existing.getId();
            var domain = User.reconstitute(
                    new User.Builder().id(new UserId(originalId)).username("u").platformRole(null),
                    AccountStatus.DISABLED, java.util.List.of());
            UserPersistenceMapper.updateEntity(existing, domain);
            assertThat(existing.getId()).isEqualTo(originalId);
        }
    }
    private UserEntity entity(String s) { var e=new UserEntity(); e.setId(UUID.randomUUID()); e.setUsername("u"); e.setAccountStatus(s); return e; }
}
