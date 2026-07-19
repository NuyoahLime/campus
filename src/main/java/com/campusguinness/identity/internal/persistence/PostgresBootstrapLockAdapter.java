package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.port.BootstrapLock;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL transaction-scoped advisory lock for SUPER_ADMIN bootstrap.
 * Uses pg_advisory_xact_lock — automatically released on commit/rollback.
 */
@Component
class PostgresBootstrapLockAdapter implements BootstrapLock {

    private final JdbcTemplate jdbc;

    PostgresBootstrapLockAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void acquireFirstSuperAdminLock() {
        // pg_advisory_xact_lock returns void — use execute() not update()
        jdbc.execute("SELECT pg_advisory_xact_lock(" + FIRST_SUPER_ADMIN_LOCK_KEY + ")");
    }
}
