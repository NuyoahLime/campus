package com.campusguinness.interfaces.web.school;

import com.campusguinness.school.application.query.model.SchoolAdminInvitationQueryResult;

import java.time.Instant;
import java.util.UUID;

public record SchoolAdminInvitationQueryResponse(
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
    static SchoolAdminInvitationQueryResponse from(SchoolAdminInvitationQueryResult result) {
        return new SchoolAdminInvitationQueryResponse(
                result.invitationId(), result.userId(), result.username(), result.schoolId(),
                result.status(), result.expiresAt(), result.acceptedAt(), result.revokedAt(),
                result.createdAt(), result.expired()
        );
    }
}
