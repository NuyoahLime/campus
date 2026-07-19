package com.campusguinness.identity.application.query;

import java.util.Optional;

/**
 * Authentication-only query port.
 * <p>
 * Separate from {@link com.campusguinness.identity.application.port.UserRepository}
 * because username/password loading is an authentication infrastructure concern,
 * not a domain aggregate repository concern.
 */
public interface AuthenticationAccountQuery {

    /**
     * Find an authentication account by login name (username).
     *
     * @param loginName the login name (username)
     * @return the account if found, otherwise empty
     */
    Optional<AuthenticationAccount> findByLoginName(String loginName);
}
