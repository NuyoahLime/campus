package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.port.UserAccountProvisioningPort;
import com.campusguinness.identity.internal.domain.User;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter for creating new users with password hash in a single INSERT.
 * <p>
 * Uses save() (not saveAndFlush) — constraint violations surface on transaction commit.
 */
@Component
class UserAccountProvisioningAdapter implements UserAccountProvisioningPort {

    private final UserJpaRepository jpa;

    UserAccountProvisioningAdapter(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public User create(User user, String passwordHash) {
        var entity = UserPersistenceMapper.toNewEntity(user, passwordHash);
        var saved = jpa.save(entity);
        return UserPersistenceMapper.toDomain(saved);
    }
}
