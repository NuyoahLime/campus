package com.campusguinness.school.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record SchoolAdminInvitationQueryResult(
        UUID invitationId,
        UUID userId,
        String username,
        UUID schoolId,
        String status,
        Instant expiresAt,
        Instant acceptedAt,
        Instant revokedAt,
        Instant createdAt,
        boolean expired
) {
}
