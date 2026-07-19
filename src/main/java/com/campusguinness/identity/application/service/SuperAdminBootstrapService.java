package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.port.*;
import com.campusguinness.identity.internal.domain.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * One-time first SUPER_ADMIN initialization.
 * Requires: empty users table, transaction-scoped advisory lock.
 */
@Service
public class SuperAdminBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrapService.class);

    private final UserBootstrapStateQuery stateQuery;
    private final BootstrapLock lock;
    private final PasswordHasher hasher;
    private final UserAccountProvisioningPort provisioning;

    public SuperAdminBootstrapService(UserBootstrapStateQuery stateQuery, BootstrapLock lock,
                                       PasswordHasher hasher, UserAccountProvisioningPort provisioning) {
        this.stateQuery = stateQuery;
        this.lock = lock;
        this.hasher = hasher;
        this.provisioning = provisioning;
    }

    /**
     * Bootstrap the first SUPER_ADMIN. Must only be called when the users table is empty.
     *
     * @param username    the admin username (pre-trimmed)
     * @param rawPassword the raw password (NOT trimmed)
     * @return bootstrap result (userId, username)
     * @throws BootstrapRefusedException if the database is not empty
     */
    @Transactional
    public BootstrapResult bootstrap(String username, String rawPassword) {
        // 1. Validate inputs
        String normalized = username != null ? username.trim() : "";
        if (normalized.isEmpty()) throw new IllegalArgumentException("username must not be blank");
        PasswordPolicy.validate(rawPassword);

        // 2. Acquire transaction-scoped lock
        lock.acquireFirstSuperAdminLock();

        // 3. Check empty table
        long count = stateQuery.countUsers();
        if (count > 0) {
            log.info("SUPER_ADMIN bootstrap refused: users table not empty ({} records)", count);
            throw new BootstrapRefusedException("Database not empty. Bootstrap can only run on an empty users table.");
        }

        // 4. Create SUPER_ADMIN domain object
        var user = User.create(new User.Builder()
                .id(new UserId(UUID.randomUUID()))
                .username(normalized)
                .platformRole("SUPER_ADMIN"));
        user.activate(); // PENDING_ACTIVATION → NORMAL

        // 5. Hash password and persist
        String passwordHash = hasher.hash(rawPassword);
        var saved = provisioning.create(user, passwordHash);

        log.info("SUPER_ADMIN bootstrap successful: userId={}, username={}", saved.id().value(), saved.username());
        return new BootstrapResult(saved.id().value(), saved.username(), saved.status().name(), saved.platformRole());
    }

    /** Result of a successful bootstrap. Contains only safe fields. */
    public record BootstrapResult(UUID userId, String username, String status, String platformRole) {}
}
