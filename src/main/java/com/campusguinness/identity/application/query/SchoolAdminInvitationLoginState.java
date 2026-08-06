package com.campusguinness.identity.application.query;

import java.time.Instant;
import java.util.UUID;

public record SchoolAdminInvitationLoginState(
        UUID invitationId,
        UUID schoolId,
        String roleInSchool,
        String invitationStatus,
        Instant expiresAt
) {}
