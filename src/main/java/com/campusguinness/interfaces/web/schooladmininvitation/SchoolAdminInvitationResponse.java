package com.campusguinness.interfaces.web.schooladmininvitation;

import com.campusguinness.identity.application.result.SchoolAdminInvitationResult;

import java.time.Instant;
import java.util.UUID;

public record SchoolAdminInvitationResponse(
        UUID userId,
        UUID invitationId,
        String username,
        UUID schoolId,
        String invitationCode,
        Instant expiresAt,
        String status
) {
    static SchoolAdminInvitationResponse from(SchoolAdminInvitationResult result) {
        return new SchoolAdminInvitationResponse(
                result.userId(),
                result.invitationId(),
                result.username(),
                result.schoolId(),
                result.invitationCode(),
                result.expiresAt(),
                result.status()
        );
    }
}
