package com.campusguinness.identity.application.query;

import java.util.UUID;
import java.time.Instant;

/**
 * Read-only authentication account model.
 * <p>
 * Exists purely for authentication infrastructure queries.
 * Must never be returned to controllers or exposed via HTTP.
 * passwordHash must never be logged.
 */
public record AuthenticationAccount(
        UUID userId,
        String loginName,
        String passwordHash,
        String accountStatus,
        String platformRole,
        Instant lockedUntil
) {}
