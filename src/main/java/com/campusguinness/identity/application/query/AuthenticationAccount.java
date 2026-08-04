package com.campusguinness.identity.application.query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
        String email,
        Instant emailVerifiedAt,
        String registrationSource,
        List<SchoolMembershipRecord> memberships,
        int loginFailures,
        Instant lockedUntil
) {
    public AuthenticationAccount(UUID userId, String loginName, String passwordHash,
            String accountStatus, String platformRole,
            List<SchoolMembershipRecord> memberships, int loginFailures, Instant lockedUntil) {
        this(userId, loginName, passwordHash, accountStatus, platformRole,
                null, null, "ADMIN_PROVISIONED", memberships, loginFailures, lockedUntil);
    }

    public record SchoolMembershipRecord(UUID schoolId, String roleInSchool) implements java.io.Serializable {
        @java.io.Serial
        private static final long serialVersionUID = 1L;
    }
}
