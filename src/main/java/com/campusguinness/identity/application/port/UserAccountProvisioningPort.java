package com.campusguinness.identity.application.port;

import com.campusguinness.identity.internal.domain.User;

/**
 * Dedicated port for creating a new user with an initial password hash.
 * <p>
 * Separate from {@link UserRepository} because new user creation
 * requires passwordHash which the domain User does not carry.
 * Existing user updates continue to use {@link UserRepository}.
 */
public interface UserAccountProvisioningPort {

    /**
     * Atomically create a new user with the given password hash.
     *
     * @param user         the domain User (must be in initial state)
     * @param passwordHash the pre-computed BCrypt password hash
     * @return the persisted domain User
     */
    User create(User user, String passwordHash);
}
