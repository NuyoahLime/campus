package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.query.AuthenticationMembership;
import com.campusguinness.identity.application.query.AuthenticationMembershipQuery;
import com.campusguinness.infrastructure.security.CurrentActor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class StudentSchoolScopeAuthorization {
    private final CurrentActor currentActor;
    private final AuthenticationMembershipQuery memberships;

    public StudentSchoolScopeAuthorization(CurrentActor currentActor, AuthenticationMembershipQuery memberships) {
        this.currentActor = currentActor;
        this.memberships = memberships;
    }

    public StudentSchoolScope requireUniqueActiveStudent() {
        UUID actorId = currentActor.requireUserId();
        List<AuthenticationMembership> active = memberships.findActiveByUserId(actorId);
        if (active.size() != 1 || !"STUDENT".equals(active.getFirst().roleInSchool())) {
            throw new IdentityApplicationException(
                    "STUDENT_SCOPE_DENIED",
                    "A unique active STUDENT membership is required.");
        }
        return new StudentSchoolScope(actorId, active.getFirst().schoolId());
    }
}
