package com.campusguinness.identity.application.query;

import java.util.UUID;

public interface SchoolAdministrationAccessQuery {

    boolean hasActiveSchoolAdminMembership(UUID userId, UUID schoolId);
}
