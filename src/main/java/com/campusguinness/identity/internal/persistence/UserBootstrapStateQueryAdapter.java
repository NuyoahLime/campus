package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.port.UserBootstrapStateQuery;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter for checking user table state during bootstrap.
 */
@Component
class UserBootstrapStateQueryAdapter implements UserBootstrapStateQuery {

    private final UserJpaRepository jpa;

    UserBootstrapStateQueryAdapter(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public long countUsers() {
        return jpa.count();
    }
}
