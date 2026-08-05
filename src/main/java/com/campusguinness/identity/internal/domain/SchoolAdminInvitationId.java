package com.campusguinness.identity.internal.domain;

import java.util.UUID;

public record SchoolAdminInvitationId(UUID value) {
    public SchoolAdminInvitationId {
        if (value == null) {
            throw new IllegalArgumentException("school admin invitation id required");
        }
    }
}
