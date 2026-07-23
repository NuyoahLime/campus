package com.campusguinness.identity.application.query.port;

import java.util.Optional;
import java.util.UUID;

public interface SchoolMembershipQueryPort {
    boolean hasActiveTeacherMembership(UUID userId, UUID schoolId);

    boolean hasActiveSchoolAdminMembership(UUID userId, UUID schoolId);

    /** Returns the schoolId for the user's ACTIVE SCHOOL_ADMIN membership, if any. */
    Optional<UUID> findActiveSchoolAdminSchoolId(UUID userId);

    /** Returns the membership ID for a teacher's ACTIVE membership in a school. */
    Optional<UUID> findActiveTeacherMembershipId(UUID userId, UUID schoolId);
}
