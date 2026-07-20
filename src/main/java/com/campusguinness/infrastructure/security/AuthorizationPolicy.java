package com.campusguinness.infrastructure.security;

import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

/**
 * Central authorization policy for school-scoped and resource-level checks.
 * Uses SchoolMembershipResolver to query school_memberships (V003).
 */
public final class AuthorizationPolicy {

    private AuthorizationPolicy() {}

    /** Require the actor to be a SCHOOL_ADMIN at the given school. */
    public static void requireSchoolAdmin(SchoolMembershipResolver resolver, UUID actorId, UUID schoolId) {
        if (!resolver.isSchoolAdmin(actorId, schoolId)) {
            throw new AccessDeniedException(
                    "Actor " + actorId + " is not SCHOOL_ADMIN at school " + schoolId);
        }
    }

    /** Require the actor to be TEACHER or SCHOOL_ADMIN at the given school. */
    public static void requireTeacherOrAbove(SchoolMembershipResolver resolver, UUID actorId, UUID schoolId) {
        if (!resolver.isTeacherOrAbove(actorId, schoolId)) {
            throw new AccessDeniedException(
                    "Actor " + actorId + " lacks teacher-level role at school " + schoolId);
        }
    }

    /** Require the actor to match the expected owner/creator. */
    public static void requireResourceOwner(UUID actorId, UUID resourceOwnerId) {
        if (!actorId.equals(resourceOwnerId)) {
            throw new AccessDeniedException(
                    "Actor " + actorId + " does not own this resource (owner: " + resourceOwnerId + ")");
        }
    }
}
