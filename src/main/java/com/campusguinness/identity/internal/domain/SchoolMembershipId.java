package com.campusguinness.identity.internal.domain;

import java.util.UUID;

/** Stable identity of a SchoolMembership child entity inside the User aggregate. */
public record SchoolMembershipId(UUID value) {
    public SchoolMembershipId {
        if (value == null) {
            throw new IllegalArgumentException("schoolMembershipId required");
        }
    }
}
