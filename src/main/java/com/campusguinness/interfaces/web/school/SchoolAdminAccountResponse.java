package com.campusguinness.interfaces.web.school;

import com.campusguinness.school.application.query.model.SchoolAdminAccountResult;

import java.time.Instant;
import java.util.UUID;

public record SchoolAdminAccountResponse(
        UUID userId,
        String username,
        String accountStatus,
        String membershipStatus,
        Instant startedAt,
        Instant lockedUntil
) {
    static SchoolAdminAccountResponse from(SchoolAdminAccountResult result) {
        return new SchoolAdminAccountResponse(
                result.userId(), result.username(), result.accountStatus(), result.membershipStatus(),
                result.startedAt(), result.lockedUntil()
        );
    }
}
