package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.exception.UsernameAlreadyExistsException;
import com.campusguinness.identity.application.port.UserAccountProvisioningPort;
import com.campusguinness.identity.internal.domain.User;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter for creating new users with password hash in a single INSERT.
 * <p>
 * Uses saveAndFlush to surface constraint violations immediately rather than
 * deferring to transaction commit, enabling precise exception translation.
 * Concurrent duplicates are caught via DataIntegrityViolationException
 * and translated to UsernameAlreadyExistsException.
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
        try {
            var saved = jpa.saveAndFlush(entity);
            return UserPersistenceMapper.toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            if (isUsernameConstraintViolation(e)) {
                throw new UsernameAlreadyExistsException(user.username());
            }
            throw e;
        }
    }

    private boolean isUsernameConstraintViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getMostSpecificCause();
        if (cause instanceof java.sql.SQLException sqlEx && "23505".equals(sqlEx.getSQLState())) {
            String msg = sqlEx.getMessage();
            if (msg != null) {
                return msg.contains("uq_users_username")
                        || msg.contains("uq_users_username_ci");
            }
        }
        return false;
    }
}
