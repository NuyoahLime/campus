package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.infrastructure.security.CurrentActor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StudentResourceAuthorization {

    private final CurrentActor currentActor;

    public StudentResourceAuthorization(CurrentActor currentActor) {
        this.currentActor = currentActor;
    }

    public UUID requireSelf(UUID ownerUserId) {
        if (ownerUserId == null) throw new IllegalArgumentException("ownerUserId required");
        UUID actorId = currentActor.requireUserId();
        if (!actorId.equals(ownerUserId)) {
            throw new IdentityApplicationException("STUDENT_SELF_SCOPE_DENIED", "Student resource scope denied.");
        }
        return actorId;
    }
}
