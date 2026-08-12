package com.campusguinness.identity.application.query;

import java.util.UUID;

public interface PlatformGovernanceAccessQuery {

    boolean hasAuthoritativeSuperAdminIdentity(UUID userId);
}
