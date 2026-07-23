package com.campusguinness.identity.application.query.port;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface SchoolMembershipQueryPort {
    boolean hasActiveTeacherMembership(UUID userId, UUID schoolId);

    boolean hasActiveSchoolAdminMembership(UUID userId, UUID schoolId);

    boolean hasActiveStudentMembership(UUID userId, UUID schoolId);

    /** Returns the schoolId for the user's ACTIVE SCHOOL_ADMIN membership, if any. */
    Optional<UUID> findActiveSchoolAdminSchoolId(UUID userId);

    /** Returns the membership ID for a teacher's ACTIVE membership in a school. */
    Optional<UUID> findActiveTeacherMembershipId(UUID userId, UUID schoolId);

    /** Returns the membership ID for a student's ACTIVE membership in a school. */
    Optional<UUID> findActiveStudentMembershipId(UUID userId, UUID schoolId);

    /** Returns all ACTIVE STUDENT membership IDs for a user. */
    List<UUID> findActiveStudentMembershipIds(UUID userId);

    /** Returns userId for each membershipId (batch lookup). */
    Map<UUID, UUID> findUserIdsByMembershipIds(List<UUID> membershipIds);
}
