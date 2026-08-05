package com.campusguinness.identity.application.result;

import com.campusguinness.identity.internal.domain.SchoolMembership;

import java.time.Instant;
import java.util.UUID;

public record SchoolMembershipResult(
        UUID membershipId,
        UUID userId,
        UUID schoolId,
        String roleInSchool,
        String status,
        Instant startedAt,
        Instant endedAt
) {
    public static SchoolMembershipResult from(UUID userId, SchoolMembership membership) {
        return new SchoolMembershipResult(
                membership.id().value(),
                userId,
                membership.schoolId(),
                membership.roleInSchool(),
                membership.status().name(),
                membership.startedAt(),
                membership.endedAt()
        );
    }
}
