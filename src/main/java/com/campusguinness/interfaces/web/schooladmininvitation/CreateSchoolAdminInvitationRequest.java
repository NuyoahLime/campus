package com.campusguinness.interfaces.web.schooladmininvitation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateSchoolAdminInvitationRequest(
        @NotBlank @Size(max = 100) String username,
        @NotNull UUID schoolId,
        Instant expiresAt
) {}
