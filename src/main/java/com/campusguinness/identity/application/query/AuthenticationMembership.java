package com.campusguinness.identity.application.query;

import java.util.UUID;

public record AuthenticationMembership(
        UUID membershipId,
        UUID schoolId,
        String roleInSchool
) {}
