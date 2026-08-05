package com.campusguinness.identity.application.result;

import com.campusguinness.identity.internal.domain.SchoolAdminInvitation;

import java.time.Instant;
import java.util.UUID;

public record SchoolAdminInvitationResult(
        UUID userId,
        UUID invitationId,
        String username,
        UUID schoolId,
        String invitationCode,
        Instant expiresAt,
        String status
) {
    public static SchoolAdminInvitationResult withRawCode(
            String username,
            SchoolAdminInvitation invitation,
            String rawInvitationCode
    ) {
        return new SchoolAdminInvitationResult(
                invitation.userId(),
                invitation.id().value(),
                username,
                invitation.schoolId(),
                rawInvitationCode,
                invitation.expiresAt(),
                invitation.status().name()
        );
    }
}
