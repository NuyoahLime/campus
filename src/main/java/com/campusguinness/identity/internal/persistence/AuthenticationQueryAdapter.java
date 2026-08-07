package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.query.AuthenticationAccount;
import com.campusguinness.identity.application.query.AuthenticationAccountQuery;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Adapter that implements {@link AuthenticationAccountQuery} using the existing
 * {@link UserJpaRepository}.
 * <p>
 * Only loads account authentication columns. SchoolMembership roles are exposed
 * through AuthenticationMembershipQuery and are not wired into Spring Security yet.
 */
@Component
class AuthenticationQueryAdapter implements AuthenticationAccountQuery {

    private final UserJpaRepository jpa;

    AuthenticationQueryAdapter(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthenticationAccount> findByLoginName(String loginName) {
        return jpa.findByUsername(loginName)
                .map(e -> new AuthenticationAccount(
                        e.getId(),
                        e.getUsername(),
                        e.getPasswordHash(),
                        e.getAccountStatus(),
                        e.getPlatformRole(),
                        e.getLockedUntil()
                ));
    }
}
