package com.campusguinness.identity.application.query;

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
        List<SchoolMembershipRecord> memberships
) {
    public record SchoolMembershipRecord(UUID schoolId, String roleInSchool) implements java.io.Serializable {
        @java.io.Serial
        private static final long serialVersionUID = 1L;
    }
}
