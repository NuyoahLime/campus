package com.campusguinness.identity.internal.domain;
import java.util.UUID;

/** School membership record — internal entity of User aggregate. References School by ID only. */
public record SchoolMembership(UUID schoolId, String roleInSchool, MembershipStatus status) {
    public SchoolMembership {
        if (schoolId == null) throw new IllegalArgumentException("schoolId required");
        if (roleInSchool == null || roleInSchool.isBlank()) throw new IllegalArgumentException("roleInSchool required");
        if (status == null) throw new IllegalArgumentException("status required");
    }
    public SchoolMembership end() { return new SchoolMembership(schoolId, roleInSchool, MembershipStatus.ENDED); }
}
