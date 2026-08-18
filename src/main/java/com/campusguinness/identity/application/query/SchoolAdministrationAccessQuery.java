package com.campusguinness.identity.application.query;

import java.util.UUID;
import java.util.List;

public interface SchoolAdministrationAccessQuery {

    boolean hasActiveSchoolAdminMembership(UUID userId, UUID schoolId);

    List<UUID> findActiveSchoolAdminSchoolIds(UUID userId);
}
