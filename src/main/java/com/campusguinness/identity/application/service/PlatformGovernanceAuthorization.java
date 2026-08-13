package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.query.PlatformGovernanceAccessQuery;
import com.campusguinness.infrastructure.security.CurrentActor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PlatformGovernanceAuthorization {

    private final CurrentActor currentActor;
    private final PlatformGovernanceAccessQuery accessQuery;

    public PlatformGovernanceAuthorization(
            CurrentActor currentActor,
            PlatformGovernanceAccessQuery accessQuery
    ) {
        this.currentActor = currentActor;
        this.accessQuery = accessQuery;
    }

    public UUID requireSuperAdmin() {
        UUID actorId = currentActor.requireUserId();
        if (!accessQuery.hasAuthoritativeSuperAdminIdentity(actorId)) {
            throw new IdentityApplicationException(
                    "PLATFORM_GOVERNANCE_DENIED",
                    "Platform governance access denied."
            );
        }
        return actorId;
    }
}
