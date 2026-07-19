package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.internal.domain.*;
import java.time.Instant;
import java.util.ArrayList;

final class UserPersistenceMapper {
    private UserPersistenceMapper() {}

    /** Create a new entity from domain �only for INSERT. Does not set auth fields. */
    static UserEntity toEntity(User domain) {
        var e = new UserEntity();
        e.setId(domain.id().value()); e.setUsername(domain.username());
        e.setAccountStatus(domain.status().name()); e.setPlatformRole(domain.platformRole());
        e.setCreatedAt(Instant.now()); e.setUpdatedAt(Instant.now());
        return e;
    }

    /**
     * Update an existing entity from domain �preserves auth fields.
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
        var memberships = new ArrayList<SchoolMembership>(); // SchoolMembership restoration deferred
        return User.reconstitute(new User.Builder()
                .id(new UserId(e.getId())).username(e.getUsername())
                .platformRole(e.getPlatformRole()),
                AccountStatus.valueOf(e.getAccountStatus()), memberships);
    }
}
