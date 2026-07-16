package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.internal.domain.*;
import java.time.Instant;
import java.util.ArrayList;

final class UserPersistenceMapper {
    private UserPersistenceMapper() {}

    static UserEntity toEntity(User domain) {
        var e = new UserEntity();
        e.setId(domain.id().value()); e.setUsername(domain.username());
        e.setAccountStatus(domain.status().name()); e.setPlatformRole(domain.platformRole());
        e.setCreatedAt(Instant.now()); e.setUpdatedAt(Instant.now());
        return e;
    }

    static User toDomain(UserEntity e) {
        var memberships = new ArrayList<SchoolMembership>(); // SchoolMembership restoration deferred
        return User.reconstitute(new User.Builder()
                .id(new UserId(e.getId())).username(e.getUsername())
                .platformRole(e.getPlatformRole()),
                AccountStatus.valueOf(e.getAccountStatus()), memberships);
    }
}
