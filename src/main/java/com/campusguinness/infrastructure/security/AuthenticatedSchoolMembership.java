package com.campusguinness.infrastructure.security;

import java.io.Serializable;
import java.util.UUID;

public record AuthenticatedSchoolMembership(
        UUID membershipId,
        UUID schoolId,
        String roleInSchool
) implements Serializable {}
