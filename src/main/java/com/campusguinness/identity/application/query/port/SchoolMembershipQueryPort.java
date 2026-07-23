package com.campusguinness.identity.application.query.port;

import java.util.UUID;

public interface SchoolMembershipQueryPort {
    boolean hasActiveTeacherMembership(UUID userId, UUID schoolId);
}
