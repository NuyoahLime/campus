package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.query.SchoolAdministrationAccessQuery;
import com.campusguinness.infrastructure.security.CurrentActor;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.List;

@Component
public class SchoolResourceAuthorization {

    private final CurrentActor currentActor;
    private final SchoolAdministrationAccessQuery accessQuery;

    public SchoolResourceAuthorization(CurrentActor currentActor, SchoolAdministrationAccessQuery accessQuery) {
        this.currentActor = currentActor;
        this.accessQuery = accessQuery;
    }

    public UUID requireSchoolAdmin(UUID schoolId) {
        if (schoolId == null) throw new IllegalArgumentException("schoolId required");
        UUID actorId = currentActor.requireUserId();
        if (!accessQuery.hasActiveSchoolAdminMembership(actorId, schoolId)) {
            throw new IdentityApplicationException("SCHOOL_ADMIN_SCOPE_DENIED", "School administration scope denied.");
        }
        return actorId;
    }

    public UUID requireUniqueSchoolAdminSchool() {
        UUID actorId = currentActor.requireUserId();
        List<UUID> schools = accessQuery.findActiveSchoolAdminSchoolIds(actorId);
        if (schools.size() != 1) {
            throw new IdentityApplicationException(
                    "SCHOOL_ADMIN_SCOPE_DENIED",
                    "A unique active school administration membership is required.");
        }
        return schools.get(0);
    }
}
