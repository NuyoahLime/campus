package com.campusguinness.identity.application.port;

/**
 * Transaction-scoped lock for first SUPER_ADMIN bootstrap.
 * Implementations must use PostgreSQL advisory lock (pg_advisory_xact_lock).
 */
public interface BootstrapLock {

    /** Lock key reserved for first SUPER_ADMIN initialization. */
    long FIRST_SUPER_ADMIN_LOCK_KEY = 789123456L;

    /**
     * Acquire a transaction-scoped exclusive lock.
     * Released automatically when the current transaction ends (commit or rollback).
     */
    void acquireFirstSuperAdminLock();
}
