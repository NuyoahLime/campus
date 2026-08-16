package com.campusguinness.school.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record SchoolAdminAccountResult(
        UUID userId,
        String username,
        String accountStatus,
        String membershipStatus,
        Instant startedAt,
        Instant lockedUntil
) {
}
