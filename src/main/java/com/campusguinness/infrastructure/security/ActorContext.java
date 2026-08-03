package com.campusguinness.infrastructure.security;

import java.util.UUID;

public record ActorContext(UUID userId, String primaryRole, UUID primarySchoolId) {

    public boolean isSuperAdmin() { return "SUPER_ADMIN".equals(primaryRole); }

    public boolean isSchoolAdmin() { return "SCHOOL_ADMIN".equals(primaryRole); }

    public UUID requireSchoolId() {
        if (primarySchoolId == null) throw new IllegalStateException("No school context");
        return primarySchoolId;
    }
}
