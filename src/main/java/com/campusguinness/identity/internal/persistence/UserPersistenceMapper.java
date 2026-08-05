package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.internal.domain.*;
import java.time.Instant;
import java.util.List;

final class UserPersistenceMapper {
    private UserPersistenceMapper() {}

    /** Create a new entity from domain — only for INSERT. Does not set auth fields. */
    static UserEntity toEntity(User domain) {
        var e = new UserEntity();
        e.setId(domain.id().value()); e.setUsername(domain.username());
        e.setAccountStatus(domain.status().name()); e.setPlatformRole(domain.platformRole());
        e.setCreatedAt(Instant.now()); e.setUpdatedAt(Instant.now());
        return e;
    }

    /**
     * Create a new entity for provisioning — explicitly sets passwordHash and auth defaults.
     * This is the only path for creating new users with credentials.
     */
    static UserEntity toNewEntity(User domain, String passwordHash) {
        var e = new UserEntity();
        e.setId(domain.id().value());
        e.setUsername(domain.username());
        e.setPasswordHash(passwordHash);
        e.setAccountStatus(domain.status().name());
        e.setPlatformRole(domain.platformRole());
        e.setLoginFailures(0);
        e.setLockedUntil(null);
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return e;
    }

    /**
     * Update an existing entity from domain — preserves auth fields.
     * Only domain-owned fields are overwritten. passwordHash, loginFailures,
     * and lockedUntil are left unchanged.
     */
    static void updateEntity(UserEntity existing, User domain) {
        existing.setUsername(domain.username());
        existing.setAccountStatus(domain.status().name());
        existing.setPlatformRole(domain.platformRole());
        existing.setUpdatedAt(Instant.now());
        // auth fields preserved: passwordHash, loginFailures, lockedUntil
    }

    static User toDomain(UserEntity e) {
        return toDomain(e, List.of());
    }

    static User toDomain(UserEntity e, List<SchoolMembership> memberships) {
        return User.reconstitute(new User.Builder()
                .id(new UserId(e.getId())).username(e.getUsername())
                .platformRole(e.getPlatformRole()),
                AccountStatus.valueOf(e.getAccountStatus()), memberships);
    }
}
